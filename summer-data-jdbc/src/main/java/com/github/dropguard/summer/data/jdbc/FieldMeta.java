package com.github.dropguard.summer.data.jdbc;

import com.github.dropguard.summer.core.Internal;

/**
 * Metadata for a single {@code @RowModel} record field: its record-component name and the
 * fully-qualified type name as reported by Jandex.
 */
@Internal
public record FieldMeta(String name, String typeName) {}
