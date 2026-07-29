mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;

@Internal
mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Metadata for a {@code @RowModel} record: the model class, its package, simple name, the physical
mport com.github.dropguard.summer.core.Internal;
 * table name, and the ordered list of mapped fields.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public record RowModelMeta(
mport com.github.dropguard.summer.core.Internal;
        String modelClassName,
mport com.github.dropguard.summer.core.Internal;
        String packageName,
mport com.github.dropguard.summer.core.Internal;
        String simpleName,
mport com.github.dropguard.summer.core.Internal;
        String tableName,
mport com.github.dropguard.summer.core.Internal;
        List<FieldMeta> fields) {}
