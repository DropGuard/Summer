package com.github.dropguard.summer.tck.negative.fixtures.data;

/**
 * Negative fixture: a type name that {@code RowMapperFactory.resolveFieldType} must reject as an
 * unsupported @RowModel field type. It intentionally lives under the negative-fixtures package (the
 * home for "framework should refuse this" samples) rather than as a one-off string literal in a
 * test, so the rejected type is real, navigable, and namespaced to the project.
 */
public final class UnsupportedNestedType {
    private UnsupportedNestedType() {}
}
