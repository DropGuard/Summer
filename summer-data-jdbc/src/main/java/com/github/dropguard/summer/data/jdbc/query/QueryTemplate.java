package com.github.dropguard.summer.data.jdbc.query;

import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.EntityMetadata;
import com.github.dropguard.summer.data.jdbc.EntityMetadataRegistry;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.List;
import java.util.Map;

/**
 * Entry point for building type-safe queries over {@code @RowModel} entities.
 *
 * <p>Inject this bean and call {@link #select(Class)} to start a fluent query. The builder reads
 * table and column metadata from the {@link EntityMetadataRegistry} (populated during assembly from
 * the same {@code @RowModel} scan as the RowMappers), so no extra index scan occurs.
 *
 * <p>Static {@code Criteria} factories ({@link #eq}, {@link #gt}, ...) are re-exported here for
 * call-site convenience: {@code queryTemplate.select(Issue.class) .where(eq("status", "OPEN"))}.
 */
@Configuration
@ConditionalOnBean(JdbcTemplate.class)
public class QueryTemplate {

    private final JdbcTemplate jdbcTemplate;
    private final EntityMetadataRegistry registry;

    public QueryTemplate(JdbcTemplate jdbcTemplate, EntityMetadataRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.registry = registry;
    }

    public <T> QueryBuilder<T> select(Class<T> entityClass) {
        EntityMetadata metadata = registry.get(entityClass);
        return new QueryBuilder<>(jdbcTemplate, entityClass, metadata, registry);
    }

    /**
     * Batch-loads the children of a set of parents in a single query and groups them by foreign key
     * — the anti-N+1 building block.
     *
     * <p>The classic N+1 is: query a list of parents (1 query), then loop each parent and query its
     * children individually (N queries). This method collapses the loop into one parameterized
     * {@code SELECT ... WHERE fkColumn IN (...)} and returns the children grouped by their
     * foreign-key value, so the caller maps them onto the already-loaded parents with a single
     * additional query instead of N.
     *
     * <p>{@code fkColumn} is validated against the child entity's registered metadata, so a raw
     * caller-supplied column string can never reach the generated SQL. An empty {@code fkValues}
     * returns an empty map without issuing a query. The foreign key is read from the mapped child
     * instance by the same field-to-column rule the {@code RowMapper} uses, so the grouping key is
     * always the child's real column value.
     *
     * @param childClass the child {@code @RowModel} entity to load (e.g. {@code Comment.class})
     * @param fkColumn the child's foreign-key column that references the parent (e.g. {@code
     *     "issue_id"})
     * @param fkValues the set of parent keys to load children for (the parents' ids)
     * @return a map from foreign-key value to the children whose {@code fkColumn} equals it; keys
     *     with no children are absent (callers may default to an empty list)
     * @throws IllegalArgumentException when {@code fkColumn} is not a known column of {@code
     *     childClass}
     */
    public <C> Map<Object, List<C>> loadByForeignKeys(
            Class<C> childClass, String fkColumn, java.util.Collection<?> fkValues) {
        EntityMetadata childMeta = registry.get(childClass);
        if (!childMeta.columns().contains(fkColumn)) {
            throw new IllegalArgumentException(
                    "Unknown column '"
                            + fkColumn
                            + "' on entity "
                            + childClass.getName()
                            + ". Known columns: "
                            + childMeta.columns());
        }
        if (fkValues == null || fkValues.isEmpty()) {
            return Map.of();
        }
        String sql =
                "SELECT * FROM "
                        + childMeta.tableName()
                        + " WHERE "
                        + fkColumn
                        + " IN ("
                        + String.join(",", java.util.Collections.nCopies(fkValues.size(), "?"))
                        + ")";
        List<C> children = jdbcTemplate.queryForList(sql, childClass, fkValues.toArray());
        Map<Object, List<C>> grouped = new java.util.LinkedHashMap<>();
        for (C child : children) {
            Object key = foreignKeyValue(childMeta, fkColumn, child);
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(child);
        }
        return grouped;
    }

    private static Object foreignKeyValue(EntityMetadata meta, String fkColumn, Object entity) {
        // Recover the field name from the snake_case column (the inverse of camelToSnake):
        // the record component getter is invoked reflectively, same as EntityAccessor.
        String fieldName = snakeToCamel(fkColumn);
        for (var field : meta.fields()) {
            if (field.name().equals(fieldName)) {
                try {
                    java.lang.reflect.Method getter = entity.getClass().getMethod(fieldName);
                    return getter.invoke(entity);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Cannot read foreign-key field '"
                                    + fieldName
                                    + "' from "
                                    + entity.getClass().getName(),
                            e);
                }
            }
        }
        throw new IllegalStateException(
                "No registered field maps to column '"
                        + fkColumn
                        + "' on "
                        + entity.getClass().getName());
    }

    /** Inverse of {@link RowMapperFactory#camelToSnake}: {@code issue_id} -> {@code issueId}. */
    private static String snakeToCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < snake.length(); i++) {
            char ch = snake.charAt(i);
            if (ch == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(ch) : ch);
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * Inserts all columns of the given entity. This is plain INSERT — not upsert; callers needing
     * "insert or update" should issue an explicit statement.
     */
    public <T> int insert(T entity) {
        EntityMetadata metadata = registry.get(entity.getClass());
        Map<String, Object> values = EntityAccessor.columnValues(metadata, entity);
        if (values.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot insert "
                            + entity.getClass().getName()
                            + ": no @RowModel fields were registered.");
        }
        String sql = buildInsertSql(metadata.tableName(), values.keySet());
        return jdbcTemplate.update(sql, values.values().toArray());
    }

    /** Alias for {@link #insert(Object)}; explicit upsert is not supported. */
    public <T> int save(T entity) {
        return insert(entity);
    }

    /**
     * Starts a full-column {@code UPDATE} for {@code entity}, located by {@code where}. Use this
     * when the entity represents the complete row state.
     */
    public MutationBuilder update(Object entity) {
        EntityMetadata metadata = registry.get(entity.getClass());
        return new MutationBuilder(
                jdbcTemplate, metadata, MutationBuilder.MutationKind.UPDATE, entity);
    }

    /**
     * Starts a partial {@code UPDATE} located by {@code where}; assign columns with {@link
     * MutationBuilder#set(String, Object)}. Prefer this for changing one or a few columns without
     * round-tripping a full entity.
     */
    public MutationBuilder update(Class<?> entityClass) {
        EntityMetadata metadata = registry.get(entityClass);
        return new MutationBuilder(
                jdbcTemplate, metadata, MutationBuilder.MutationKind.UPDATE, null);
    }

    /** Starts a {@code DELETE} for {@code entityClass}, located by {@code where}. */
    public MutationBuilder delete(Class<?> entityClass) {
        EntityMetadata metadata = registry.get(entityClass);
        return new MutationBuilder(
                jdbcTemplate, metadata, MutationBuilder.MutationKind.DELETE, null);
    }

    private static String buildInsertSql(String table, java.util.Set<String> columns) {
        String colList = String.join(", ", columns);
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        return "INSERT INTO " + table + " (" + colList + ") VALUES (" + placeholders + ")";
    }

    // ── Criteria factories (convenience re-exports) ─────────────────

    public static Criteria eq(String column, Object value) {
        return new Criteria.Eq(column, value);
    }

    /**
     * {@code column IN (values...)} — batch-loading predicate: matches any row whose column is in
     * the given value set. An empty set matches nothing (rendered as a contradiction), so callers
     * can batch-load children by an empty parent-id set without invalid SQL.
     */
    public static Criteria in(String column, java.util.Collection<?> values) {
        return new Criteria.In(column, values == null ? List.of() : List.copyOf(values));
    }

    /**
     * Column-to-column equality for join / EXISTS {@code ON} predicates, where the right-hand side
     * is another column rather than a bind value. Both sides must be qualified with a known table
     * alias (or be a bare root column).
     */
    public static Criteria eqCol(String leftColumn, String rightColumn) {
        return new Criteria.ColEq(leftColumn, rightColumn);
    }

    public static Criteria gt(String column, Object value) {
        return new Criteria.Comparison(column, ">", value);
    }

    public static Criteria ge(String column, Object value) {
        return new Criteria.Comparison(column, ">=", value);
    }

    public static Criteria lt(String column, Object value) {
        return new Criteria.Comparison(column, "<", value);
    }

    public static Criteria le(String column, Object value) {
        return new Criteria.Comparison(column, "<=", value);
    }

    public static Criteria like(String column, String value) {
        return new Criteria.Like(column, value);
    }

    public static Criteria isNull(String column) {
        return new Criteria.IsNull(column);
    }

    public static Criteria and(Criteria... parts) {
        return new Criteria.Composite("AND", List.of(parts));
    }

    public static Criteria or(Criteria... parts) {
        return new Criteria.Composite("OR", List.of(parts));
    }
}
