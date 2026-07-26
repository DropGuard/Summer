package com.github.dropguard.summer.data.jdbc;

import java.util.List;

/**
 * Metadata for a {@code @RowModel} record: the model class, its package, simple
 * name, the physical table name, and the ordered list of mapped fields.
 */
public record RowModelMeta(String modelClassName, String packageName, String simpleName, String tableName,
		List<FieldMeta> fields) {
}
