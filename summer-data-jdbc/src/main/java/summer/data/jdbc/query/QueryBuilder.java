package summer.data.jdbc.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import summer.data.jdbc.EntityMetadata;
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
 * @param <T>
 *            the entity type
 */
public final class QueryBuilder<T> {

	private final JdbcTemplate jdbcTemplate;
	private final Class<T> entityClass;
	private final EntityMetadata metadata;

	private final List<Criteria> where = new ArrayList<>();
	private String orderByColumn;
	private boolean orderDesc;
	private Integer limit;
	private Integer offset;

	QueryBuilder(JdbcTemplate jdbcTemplate, Class<T> entityClass, EntityMetadata metadata) {
		this.jdbcTemplate = jdbcTemplate;
		this.entityClass = entityClass;
		this.metadata = metadata;
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

	private String buildSelectSql() {
		StringBuilder sql = new StringBuilder("SELECT * FROM ").append(metadata.tableName());
		appendWhere(sql);
		appendOrderByLimit(sql);
		return sql.toString();
	}

	private String buildCountSql() {
		StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(metadata.tableName());
		appendWhere(sql);
		return sql.toString();
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
			sql.append(" ORDER BY ").append(orderByColumn).append(orderDesc ? " DESC" : " ASC");
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
		Set<String> known = metadata.columns();
		for (Criteria c : where) {
			for (String col : c.columns()) {
				if (!known.contains(col)) {
					throw new IllegalArgumentException("Unknown column '" + col + "' on entity " + entityClass.getName()
							+ ". Known columns: " + known);
				}
			}
		}
		if (orderByColumn != null && !known.contains(orderByColumn)) {
			throw new IllegalArgumentException("Unknown order-by column '" + orderByColumn + "' on entity "
					+ entityClass.getName() + ". Known columns: " + known);
		}
	}

	private void requireColumn(String column) {
		if (!metadata.columns().contains(column)) {
			throw new IllegalArgumentException("Unknown column '" + column + "' on entity " + entityClass.getName()
					+ ". Known columns: " + metadata.columns());
		}
	}
}
