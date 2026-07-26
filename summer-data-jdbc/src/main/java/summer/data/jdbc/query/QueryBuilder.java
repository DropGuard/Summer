package summer.data.jdbc.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import summer.data.jdbc.EntityMetadata;
import summer.data.jdbc.EntityMetadataRegistry;
import summer.data.jdbc.JdbcTemplate;

/**
 * Fluent, type-safe SQL builder for a single {@code @RowModel} entity.
 *
 * <p>
 * Produced by {@link QueryTemplate#select(Class)}. Column identifiers are taken
 * only from the entity's registered metadata (never from raw user strings that
 * reach the SQL text); values are always bound as {@code ?} parameters via
 * {@link JdbcTemplate}. The result rows are mapped back through the entity's
 * registered {@code RowMapper}, so no mapping code lives here.
 * </p>
 *
 * <p>
 * Scope: this builder covers a <em>single</em> {@code @RowModel} entity as its
 * root, but supports the two relationship operations that account for almost
 * all real query needs without a full join DSL:
 * <ul>
 * <li>{@link #join(Class, String, Criteria)} — bring in a 1:1 / N:1 related
 * table (e.g. project details) by its primary key;</li>
 * <li>{@code where(exists(...))} — filter the root by a relationship that may
 * multiply (many-to-many tag filter, "has any comment") using a
 * {@code WHERE EXISTS} sub-query, which never multiplies root rows and keeps
 * {@link #count()} / pagination correct.</li>
 * </ul>
 * Column identifiers still come only from registered metadata. A joined or
 * {@code EXISTS}-referenced table is declared up front, so a qualified column
 * such as {@code issue_tags.tag_id} is validated against <em>that</em> table's
 * metadata rather than the root's — user-supplied column strings still never
 * reach the SQL text. Arbitrary join trees (outer joins, nested ON groups) are
 * intentionally out of scope; express those as hand-written SQL through
 * {@link JdbcTemplate}.
 * </p>
 *
 * @param <T>
 *            the entity type
 */
public final class QueryBuilder<T> {

	private final JdbcTemplate jdbcTemplate;
	private final Class<T> entityClass;
	private final EntityMetadata metadata;
	private final EntityMetadataRegistry registry;

	private final List<Criteria> where = new ArrayList<>();
	// Declared related tables (alias -> metadata) used for qualified-column
	// validation and FROM/JOIN SQL assembly. Keyed by alias so callers reference
	// columns unambiguously as "alias.column".
	private final Map<String, EntityMetadata> joins = new LinkedHashMap<>();
	private String orderByColumn;
	private boolean orderDesc;
	private Integer limit;
	private Integer offset;

	QueryBuilder(JdbcTemplate jdbcTemplate, Class<T> entityClass, EntityMetadata metadata,
			EntityMetadataRegistry registry) {
		this.jdbcTemplate = jdbcTemplate;
		this.entityClass = entityClass;
		this.metadata = metadata;
		this.registry = registry;
	}

	/** Adds one or more {@code AND}-combined conditions. */
	public QueryBuilder<T> where(Criteria... criteria) {
		for (Criteria c : criteria) {
			where.add(c);
		}
		return this;
	}

	/** Combines the given conditions with {@code AND} as one group. */
	public QueryBuilder<T> and(Criteria... criteria) {
		where.add(new Criteria.Composite("AND", List.of(criteria)));
		return this;
	}

	/** Combines the given conditions with {@code OR} as one group. */
	public QueryBuilder<T> or(Criteria... criteria) {
		where.add(new Criteria.Composite("OR", List.of(criteria)));
		return this;
	}

	/**
	 * Brings a related 1:1 / N:1 table into the query. The related entity must be a
	 * registered {@code @RowModel}; {@code on} is the join predicate expressed with
	 * qualified columns (e.g. {@code eq("p.id", "issues.project_id")}). Use this
	 * only for relationships that multiply the root at most once — for many-to-many
	 * filtering prefer {@code where(exists(...))}, which does not inflate row
	 * counts and keeps pagination correct.
	 *
	 * @param related
	 *            the related {@code @RowModel} entity class
	 * @param alias
	 *            the alias to reference the table's columns by (e.g. {@code "p"});
	 *            must be used as the column prefix in the {@code on} predicate
	 * @param on
	 *            the join predicate, validated against both tables' metadata
	 */
	public QueryBuilder<T> join(Class<?> related, String alias, Criteria on) {
		EntityMetadata relatedMeta = registry.get(related);
		joins.put(alias, relatedMeta);
		where.add(new Criteria.JoinPredicate(alias, relatedMeta.tableName(), on));
		return this;
	}

	/**
	 * Convenience for {@code where(exists(related, alias, on))}: filters the root
	 * by a relationship that may multiply (many-to-many) using a
	 * {@code WHERE EXISTS} sub-query. Unlike {@link #join(Class, String, Criteria)}
	 * this never duplicates root rows, so {@link #count()} and pagination stay
	 * correct even when an issue has several matching tags.
	 */
	public QueryBuilder<T> exists(Class<?> related, String alias, Criteria on) {
		EntityMetadata relatedMeta = registry.get(related);
		joins.put(alias, relatedMeta);
		where.add(new Criteria.ExistsPredicate(alias, relatedMeta.tableName(), on));
		return this;
	}

	/** Orders results by a known column (validated against entity metadata). */
	public QueryBuilder<T> orderBy(String column) {
		requireColumn(column);
		this.orderByColumn = column;
		this.orderDesc = false;
		return this;
	}

	public QueryBuilder<T> desc() {
		this.orderDesc = true;
		return this;
	}

	public QueryBuilder<T> asc() {
		this.orderDesc = false;
		return this;
	}

	public QueryBuilder<T> limit(int limit) {
		if (limit < 0) {
			throw new IllegalArgumentException("limit must be >= 0");
		}
		this.limit = limit;
		return this;
	}

	public QueryBuilder<T> offset(int offset) {
		if (offset < 0) {
			throw new IllegalArgumentException("offset must be >= 0");
		}
		this.offset = offset;
		return this;
	}

	/** Returns all matching rows mapped to the entity type. */
	public List<T> list() {
		validateColumns();
		return jdbcTemplate.queryForList(buildSelectSql(), entityClass, boundParams());
	}

	/** Returns the first matching row, or {@code null} if none. */
	@SuppressWarnings("unchecked")
	public T first() {
		validateColumns();
		List<T> rows = jdbcTemplate.queryForList(buildSelectSql() + " LIMIT 1", entityClass, boundParams());
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** Returns the number of matching rows. */
	public long count() {
		validateColumns();
		Integer result = jdbcTemplate.queryForObject(buildCountSql(), Integer.class, boundParams());
		return result == null ? 0L : result;
	}

	// ── SQL assembly ─────────────────────────────────────────────────

	private static final String ROOT_ALIAS = "root";

	private String buildSelectSql() {
		StringBuilder sql = new StringBuilder("SELECT ");
		if (joins.isEmpty()) {
			sql.append("*");
		} else {
			// Qualify the root to avoid column collisions with joined tables.
			sql.append(ROOT_ALIAS).append(".*");
		}
		sql.append(" FROM ").append(metadata.tableName()).append(" ").append(ROOT_ALIAS);
		appendJoins(sql);
		appendWhere(sql);
		appendOrderByLimit(sql);
		return sql.toString();
	}

	private String buildCountSql() {
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(metadata.tableName()).append(" ")
				.append(ROOT_ALIAS);
		appendJoins(sql);
		appendWhere(sql);
		return sql.toString();
	}

	private void appendJoins(StringBuilder sql) {
		for (Map.Entry<String, EntityMetadata> e : joins.entrySet()) {
			// JOIN predicates carry their own ON clause; only those that introduce
			// a JOIN (not the EXISTS sub-queries) emit a FROM-level join here.
			EntityMetadata meta = e.getValue();
			for (Criteria c : where) {
				if (c instanceof Criteria.JoinPredicate jp && jp.alias().equals(e.getKey())) {
					sql.append(" JOIN ").append(meta.tableName()).append(" ").append(jp.alias()).append(" ON ")
							.append(jp.on().render().fragment());
				}
			}
		}
	}

	private void appendWhere(StringBuilder sql) {
		if (where.isEmpty()) {
			return;
		}
		sql.append(" WHERE ");
		List<String> fragments = new ArrayList<>();
		for (Criteria c : where) {
			fragments.add(c.render().fragment());
		}
		sql.append(String.join(" AND ", fragments));
	}

	private void appendOrderByLimit(StringBuilder sql) {
		if (orderByColumn != null) {
			sql.append(" ORDER BY ").append(qualifyIfNeeded(orderByColumn)).append(orderDesc ? " DESC" : " ASC");
		}
		if (limit != null) {
			sql.append(" LIMIT ").append(limit);
		}
		if (offset != null) {
			sql.append(" OFFSET ").append(offset);
		}
	}

	private Object[] boundParams() {
		List<Object> params = new ArrayList<>();
		for (Criteria c : where) {
			params.addAll(c.render().params());
		}
		return params.toArray();
	}

	private void validateColumns() {
		for (Criteria c : where) {
			for (String col : c.columns()) {
				requireQualifiedOrRootColumn(col);
			}
		}
		if (orderByColumn != null) {
			requireQualifiedOrRootColumn(orderByColumn);
		}
	}

	/**
	 * A bare column is validated against the root entity; a {@code "alias.column"}
	 * reference is validated against the metadata of the declared related table
	 * with that alias. This keeps user-supplied column strings off the SQL text
	 * while permitting references to joined / EXISTS tables.
	 */
	private void requireQualifiedOrRootColumn(String column) {
		int dot = column.indexOf('.');
		if (dot < 0) {
			if (!metadata.columns().contains(column)) {
				throw new IllegalArgumentException("Unknown column '" + column + "' on entity " + entityClass.getName()
						+ ". Known columns: " + metadata.columns());
			}
			return;
		}
		String alias = column.substring(0, dot);
		String name = column.substring(dot + 1);
		// The root entity is addressable as ROOT_ALIAS; join/exists tables by their
		// declared alias. Both map to real metadata so qualification never weakens
		// the column whitelist.
		EntityMetadata related = ROOT_ALIAS.equals(alias) ? metadata : joins.get(alias);
		if (related == null) {
			throw new IllegalArgumentException("Unknown table alias '" + alias + "' in column '" + column
					+ "'. Declare it via join(...) or exists(...) first.");
		}
		if (!related.columns().contains(name)) {
			throw new IllegalArgumentException("Unknown column '" + name + "' on table aliased '" + alias
					+ "'. Known columns: " + related.columns());
		}
	}

	private String qualifyIfNeeded(String column) {
		return column.indexOf('.') < 0 ? ROOT_ALIAS + "." + column : column;
	}

	private void requireColumn(String column) {
		requireQualifiedOrRootColumn(column);
	}
}
