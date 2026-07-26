package com.github.dropguard.summer.data.jdbc.query;

import com.github.dropguard.summer.core.exception.MissingWhereClauseException;
import com.github.dropguard.summer.data.jdbc.EntityMetadata;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for {@code UPDATE} and {@code DELETE} statements.
 *
 * <p>
 * Both operations require an explicit {@code where} clause: issuing an
 * unqualified mutation would touch every row, so a missing condition fails fast
 * with {@link MissingWhereClauseException} rather than silently hitting the
 * whole table.
 * </p>
 *
 * <p>
 * An {@code UPDATE} sets either the entity's full column set (when built from
 * {@code update(entity)}) or only the columns named by
 * {@link #set(String, Object)} (when built from {@code update(Type).set(...)}).
 * Full-column update models the "entity is the complete row state" mental
 * model; partial update is the day-to-day path for changing one or a few
 * columns. Concurrent updates are serialised by the database at the row level
 * under standard transaction isolation, so no extra versioning mechanism is
 * required.
 * </p>
 */
public final class MutationBuilder {

	private final JdbcTemplate jdbcTemplate;
	private final EntityMetadata metadata;
	private final MutationKind kind;
	private final Object entity;

	private final List<Criteria> where = new ArrayList<>();
	private final Map<String, Object> explicitSets = new LinkedHashMap<>();

	MutationBuilder(JdbcTemplate jdbcTemplate, EntityMetadata metadata, MutationKind kind, Object entity) {
		this.jdbcTemplate = jdbcTemplate;
		this.metadata = metadata;
		this.kind = kind;
		this.entity = entity;
	}

	public MutationBuilder where(Criteria... criteria) {
		for (Criteria c : criteria) {
			where.add(c);
		}
		return this;
	}

	/**
	 * Adds a column assignment for a partial update. Column names are validated
	 * against the entity's known columns. Cannot be combined with a full-column
	 * {@code update(entity)} — when {@code entity} is present, its columns are used
	 * instead.
	 */
	public MutationBuilder set(String column, Object value) {
		if (!metadata.columns().contains(column)) {
			throw new IllegalArgumentException("Unknown column '" + column + "' on entity " + metadata.tableName()
					+ ". Known columns: " + metadata.columns());
		}
		explicitSets.put(column, value);
		return this;
	}

	public int execute() {
		if (where.isEmpty()) {
			throw new MissingWhereClauseException(
					kind + " requires a WHERE clause to avoid touching every row in " + metadata.tableName());
		}
		validateWhereColumns();
		Map<String, Object> setValues = resolveSetValues();
		List<Object> params = new ArrayList<>(setValues.values());
		for (Criteria c : where) {
			params.addAll(c.render().params());
		}
		return jdbcTemplate.update(buildSql(setValues), params.toArray());
	}

	private Map<String, Object> resolveSetValues() {
		if (kind != MutationKind.UPDATE) {
			return Map.of();
		}
		if (!explicitSets.isEmpty()) {
			return explicitSets;
		}
		if (entity != null) {
			return EntityAccessor.columnValues(metadata, entity);
		}
		throw new IllegalStateException("UPDATE has no columns to set: provide an entity via update(entity) "
				+ "or explicit assignments via set(column, value).");
	}

	private String buildSql(Map<String, Object> setValues) {
		StringBuilder sql = new StringBuilder();
		if (kind == MutationKind.UPDATE) {
			sql.append("UPDATE ").append(metadata.tableName()).append(" SET ");
			List<String> assignments = new ArrayList<>();
			for (String column : setValues.keySet()) {
				assignments.add(column + " = ?");
			}
			sql.append(String.join(", ", assignments));
		} else {
			sql.append("DELETE FROM ").append(metadata.tableName());
		}
		sql.append(" WHERE ");
		List<String> fragments = new ArrayList<>();
		for (Criteria c : where) {
			fragments.add(c.render().fragment());
		}
		sql.append(String.join(" AND ", fragments));
		return sql.toString();
	}

	private void validateWhereColumns() {
		Set<String> known = metadata.columns();
		for (Criteria c : where) {
			for (String col : c.columns()) {
				if (!known.contains(col)) {
					throw new IllegalArgumentException("Unknown column '" + col + "' on entity " + metadata.tableName()
							+ ". Known columns: " + known);
				}
			}
		}
	}

	enum MutationKind {
		UPDATE, DELETE
	}
}
