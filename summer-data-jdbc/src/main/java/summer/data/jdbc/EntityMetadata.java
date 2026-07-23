package summer.data.jdbc;

import java.util.List;
import java.util.Set;

/**
 * Resolved metadata for a {@code @RowModel} entity, used by QueryBuilder to
 * author table and column names safely.
 *
 * <p>
 * {@code columns} is the set of legal column names (derived from the entity's
 * record components via the same camelCase-to-snake_case rule the RowMapper
 * uses). QueryBuilder rejects any column not present here, so user-supplied
 * column strings can never reach generated SQL — the only SQL identifiers
 * QueryBuilder emits are the table name and these known columns.
 * </p>
 *
 * <p>
 * {@code fields} preserves the field-to-column mapping from the single Jandex
 * scan. Mutation builders use it to bind an entity's values without performing
 * another index scan.
 * </p>
 */
public record EntityMetadata(String tableName, Set<String> columns, List<RowMapperFactory.FieldMeta> fields) {

	public EntityMetadata {
		columns = Set.copyOf(columns);
		fields = List.copyOf(fields);
	}

	public boolean hasColumn(String column) {
		return columns.contains(column);
	}
}
