package summer.data.jdbc.query;

import java.util.List;
import java.util.Map;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.data.jdbc.EntityMetadata;
import summer.data.jdbc.EntityMetadataRegistry;
import summer.data.jdbc.JdbcTemplate;

/**
 * Entry point for building type-safe queries over {@code @RowModel} entities.
 *
 * <p>
 * Inject this bean and call {@link #select(Class)} to start a fluent query. The
 * builder reads table and column metadata from the
 * {@link EntityMetadataRegistry} (populated during assembly from the same
 * {@code @RowModel} scan as the RowMappers), so no extra index scan occurs.
 * </p>
 *
 * <p>
 * Static {@code Criteria} factories ({@link #eq}, {@link #gt}, ...) are
 * re-exported here for call-site convenience:
 * {@code queryTemplate.select(Issue.class)
 * .where(eq("status", "OPEN"))}.
 * </p>
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
		return new QueryBuilder<>(jdbcTemplate, entityClass, metadata);
	}

	/**
	 * Inserts all columns of the given entity. This is plain INSERT — not upsert;
	 * callers needing "insert or update" should issue an explicit statement.
	 */
	public <T> int insert(T entity) {
		EntityMetadata metadata = registry.get(entity.getClass());
		Map<String, Object> values = EntityAccessor.columnValues(metadata, entity);
		if (values.isEmpty()) {
			throw new IllegalStateException(
					"Cannot insert " + entity.getClass().getName() + ": no @RowModel fields were registered.");
		}
		String sql = buildInsertSql(metadata.tableName(), values.keySet());
		return jdbcTemplate.update(sql, values.values().toArray());
	}

	/** Alias for {@link #insert(Object)}; explicit upsert is not supported. */
	public <T> int save(T entity) {
		return insert(entity);
	}

	/**
	 * Starts a full-column {@code UPDATE} for {@code entity}, located by
	 * {@code where}. Use this when the entity represents the complete row state.
	 */
	public MutationBuilder update(Object entity) {
		EntityMetadata metadata = registry.get(entity.getClass());
		return new MutationBuilder(jdbcTemplate, metadata, MutationBuilder.MutationKind.UPDATE, entity);
	}

	/**
	 * Starts a partial {@code UPDATE} located by {@code where}; assign columns with
	 * {@link MutationBuilder#set(String, Object)}. Prefer this for changing one or
	 * a few columns without round-tripping a full entity.
	 */
	public MutationBuilder update(Class<?> entityClass) {
		EntityMetadata metadata = registry.get(entityClass);
		return new MutationBuilder(jdbcTemplate, metadata, MutationBuilder.MutationKind.UPDATE, null);
	}

	/**
	 * Starts a {@code DELETE} for {@code entityClass}, located by {@code where}.
	 */
	public MutationBuilder delete(Class<?> entityClass) {
		EntityMetadata metadata = registry.get(entityClass);
		return new MutationBuilder(jdbcTemplate, metadata, MutationBuilder.MutationKind.DELETE, null);
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
