mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Set;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Resolved metadata for a {@code @RowModel} entity, used by QueryBuilder to author table and column
mport com.github.dropguard.summer.core.Internal;
 * names safely.
mport com.github.dropguard.summer.core.Internal;
@Internal
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>{@code columns} is the set of legal column names (derived from the entity's record components
mport com.github.dropguard.summer.core.Internal;
 * via the same camelCase-to-snake_case rule the RowMapper uses). QueryBuilder rejects any column
mport com.github.dropguard.summer.core.Internal;
 * not present here, so user-supplied column strings can never reach generated SQL — the only SQL
mport com.github.dropguard.summer.core.Internal;
 * identifiers QueryBuilder emits are the table name and these known columns.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>{@code fields} preserves the field-to-column mapping from the single Jandex scan. Mutation
mport com.github.dropguard.summer.core.Internal;
 * builders use it to bind an entity's values without performing another index scan.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public record EntityMetadata(String tableName, Set<String> columns, List<FieldMeta> fields) {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public EntityMetadata {
mport com.github.dropguard.summer.core.Internal;
        columns = Set.copyOf(columns);
mport com.github.dropguard.summer.core.Internal;
        fields = List.copyOf(fields);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public boolean hasColumn(String column) {
mport com.github.dropguard.summer.core.Internal;
        return columns.contains(column);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
