package summer.data.jdbc;

/**
 * Metadata for a single {@code @RowModel} record field: its record-component
 * name and the fully-qualified type name as reported by Jandex.
 */
public record FieldMeta(String name, String typeName) {
}
