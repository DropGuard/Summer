mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.fasterxml.jackson.databind.ObjectMapper;
mport com.github.dropguard.summer.core.Internal;
import java.sql.ResultSet;
mport com.github.dropguard.summer.core.Internal;
import java.sql.SQLException;
mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.AnnotationValue;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.ClassInfo;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.DotName;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.IndexView;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.RecordComponentInfo;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Discovers {@code @RowModel} records through a Jandex index and builds the {@link RowMapper}
@Internal
mport com.github.dropguard.summer.core.Internal;
 * instances both engines need.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Two responsibilities live here, kept as stateless entry points because they are pure functions
mport com.github.dropguard.summer.core.Internal;
 * of their inputs (no shared mutable state):
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <ul>
mport com.github.dropguard.summer.core.Internal;
 *   <li>{@link #scanJandex(IndexView)} — the single discovery pass; it also validates every field
mport com.github.dropguard.summer.core.Internal;
 *       type up front, so an unsupported mapping fails fast at assembly rather than at row-mapping
mport com.github.dropguard.summer.core.Internal;
 *       time.
mport com.github.dropguard.summer.core.Internal;
 *   <li>{@link #createReflective(RowModelMeta)} — builds the runtime reflective mapper used by the
mport com.github.dropguard.summer.core.Internal;
 *       Runtime DI engine when no code generation is available.
mport com.github.dropguard.summer.core.Internal;
 * </ul>
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * The AOT engine instead emits inline mappers at build time (see {@code WireMethodGenerator} +
mport com.github.dropguard.summer.core.Internal;
 * {@code TypeReads.jdbcRead}); it reuses {@link #scanJandex(IndexView)} and the {@link
mport com.github.dropguard.summer.core.Internal;
 * #resolveFieldType(String)} contract so both engines share one type truth.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class RowMapperFactory {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final DotName ROW_MODEL_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.data.jdbc.annotation.RowModel");
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private RowMapperFactory() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * The single discovery pass: scans a Jandex index for {@code @RowModel} records and extracts
mport com.github.dropguard.summer.core.Internal;
     * field metadata. Every field type is validated here via {@link #resolveFieldType(String)}, so
mport com.github.dropguard.summer.core.Internal;
     * an unsupported mapping surfaces as a clear error at assembly time on both engines — not as a
mport com.github.dropguard.summer.core.Internal;
     * row-mapping surprise at runtime.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static List<RowModelMeta> scanJandex(IndexView index) {
mport com.github.dropguard.summer.core.Internal;
        List<RowModelMeta> result = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (ClassInfo ci : index.getKnownClasses()) {
mport com.github.dropguard.summer.core.Internal;
            if (ci.isAnnotation() || ci.isInterface()) {
mport com.github.dropguard.summer.core.Internal;
                continue;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (!ci.hasAnnotation(ROW_MODEL_DOT)) {
mport com.github.dropguard.summer.core.Internal;
                continue;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            // recordComponents() returns components in sorted (non-declaration)
mport com.github.dropguard.summer.core.Internal;
            // order in this Jandex version; the canonical constructor and therefore
mport com.github.dropguard.summer.core.Internal;
            // the record's actual field order follow the declaration order. Use
mport com.github.dropguard.summer.core.Internal;
            // recordComponentsInDeclarationOrder() so downstream consumers that
mport com.github.dropguard.summer.core.Internal;
            // build a constructor invocation positionally (the AOT inline RowMapper)
mport com.github.dropguard.summer.core.Internal;
            // stay aligned with the record's real signature. Runtime reflective
mport com.github.dropguard.summer.core.Internal;
            // mapping is unaffected (it maps by name, not position).
mport com.github.dropguard.summer.core.Internal;
            List<RecordComponentInfo> components = ci.recordComponentsInDeclarationOrder();
mport com.github.dropguard.summer.core.Internal;
            if (components == null || components.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                continue;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            List<FieldMeta> fields = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
            for (RecordComponentInfo comp : components) {
mport com.github.dropguard.summer.core.Internal;
                // Validate the type up front (fail-fast, shared by both engines).
mport com.github.dropguard.summer.core.Internal;
                resolveFieldType(comp.type().name().toString());
mport com.github.dropguard.summer.core.Internal;
                fields.add(new FieldMeta(comp.name(), comp.type().name().toString()));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            // Jandex returns null (not the annotation default) when table is unset,
mport com.github.dropguard.summer.core.Internal;
            // so guard against it explicitly; an empty table fails fast at assembly.
mport com.github.dropguard.summer.core.Internal;
            AnnotationValue tableValue = ci.classAnnotation(ROW_MODEL_DOT).value("table");
mport com.github.dropguard.summer.core.Internal;
            String tableName = tableValue != null ? tableValue.asString() : "";
mport com.github.dropguard.summer.core.Internal;
            if (tableName == null || tableName.isBlank()) {
mport com.github.dropguard.summer.core.Internal;
                throw new IllegalStateException(
mport com.github.dropguard.summer.core.Internal;
                        "@RowModel on "
mport com.github.dropguard.summer.core.Internal;
                                + ci.name()
mport com.github.dropguard.summer.core.Internal;
                                + " must declare a non-empty table() — the physical table name is"
mport com.github.dropguard.summer.core.Internal;
                                + " required.");
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            result.add(
mport com.github.dropguard.summer.core.Internal;
                    new RowModelMeta(
mport com.github.dropguard.summer.core.Internal;
                            ci.name().toString(),
mport com.github.dropguard.summer.core.Internal;
                            ci.name().packagePrefix(),
mport com.github.dropguard.summer.core.Internal;
                            ci.name().withoutPackagePrefix(),
mport com.github.dropguard.summer.core.Internal;
                            tableName,
mport com.github.dropguard.summer.core.Internal;
                            fields));
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return result;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Creates a reflective {@code RowMapper} at runtime using Jackson {@code ObjectMapper}. Used by
mport com.github.dropguard.summer.core.Internal;
     * the runtime DI engine when no code generation is available.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    @SuppressWarnings("unchecked")
mport com.github.dropguard.summer.core.Internal;
    public static RowMapper<?> createReflective(RowModelMeta meta) {
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            Class<?> modelClass = Class.forName(meta.modelClassName());
mport com.github.dropguard.summer.core.Internal;
            return new ReflectiveRowMapper<>(modelClass, meta);
mport com.github.dropguard.summer.core.Internal;
        } catch (ClassNotFoundException e) {
mport com.github.dropguard.summer.core.Internal;
            throw new IllegalStateException(
mport com.github.dropguard.summer.core.Internal;
                    "Cannot load @RowModel class: " + meta.modelClassName(), e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Converts a camelCase field name to a snake_case column name, the standard SQL naming
mport com.github.dropguard.summer.core.Internal;
     * convention. Example: {@code createdAt} becomes {@code created_at}.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static String camelToSnake(String camelCase) {
mport com.github.dropguard.summer.core.Internal;
        StringBuilder sb = new StringBuilder(camelCase.length() + 4);
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i < camelCase.length(); i++) {
mport com.github.dropguard.summer.core.Internal;
            char ch = camelCase.charAt(i);
mport com.github.dropguard.summer.core.Internal;
            if (Character.isUpperCase(ch)) {
mport com.github.dropguard.summer.core.Internal;
                if (i > 0) {
mport com.github.dropguard.summer.core.Internal;
                    sb.append('_');
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                sb.append(Character.toLowerCase(ch));
mport com.github.dropguard.summer.core.Internal;
            } else {
mport com.github.dropguard.summer.core.Internal;
                sb.append(ch);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return sb.toString();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final class ReflectiveRowMapper<T> implements RowMapper<T> {
mport com.github.dropguard.summer.core.Internal;
        private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
mport com.github.dropguard.summer.core.Internal;
        private final Class<T> modelClass;
mport com.github.dropguard.summer.core.Internal;
        private final String[] fieldNames;
mport com.github.dropguard.summer.core.Internal;
        private final String[] columnNames;
mport com.github.dropguard.summer.core.Internal;
        private final Class<?>[] fieldTypes;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        ReflectiveRowMapper(Class<T> modelClass, RowModelMeta meta) {
mport com.github.dropguard.summer.core.Internal;
            this.modelClass = modelClass;
mport com.github.dropguard.summer.core.Internal;
            List<String> fields = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
            List<String> cols = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
            List<Class<?>> types = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
            for (FieldMeta f : meta.fields()) {
mport com.github.dropguard.summer.core.Internal;
                fields.add(f.name());
mport com.github.dropguard.summer.core.Internal;
                cols.add(camelToSnake(f.name()));
mport com.github.dropguard.summer.core.Internal;
                types.add(resolveFieldType(f.typeName()));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            this.fieldNames = fields.toArray(String[]::new);
mport com.github.dropguard.summer.core.Internal;
            this.columnNames = cols.toArray(String[]::new);
mport com.github.dropguard.summer.core.Internal;
            this.fieldTypes = types.toArray(Class<?>[]::new);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        @Override
mport com.github.dropguard.summer.core.Internal;
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
            Map<String, Object> values = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
            for (int i = 0; i < fieldNames.length; i++) {
mport com.github.dropguard.summer.core.Internal;
                values.put(fieldNames[i], rs.getObject(columnNames[i], fieldTypes[i]));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return MAPPER.convertValue(values, modelClass);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * The single type contract for {@code @RowModel} fields: maps a field's type name to the Java
mport com.github.dropguard.summer.core.Internal;
     * type used for JDBC's native {@code ResultSet.getObject(col, type)} read. Only JDBC-native
mport com.github.dropguard.summer.core.Internal;
     * types are supported; anything else fails fast so an unsupported mapping never reaches
mport com.github.dropguard.summer.core.Internal;
     * row-mapping time.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <p>This is the one source of truth shared by the runtime reflective mapper and the AOT
mport com.github.dropguard.summer.core.Internal;
     * engine's generated inline mappers (the AOT side resolves the canonical name to emit {@code
mport com.github.dropguard.summer.core.Internal;
     * X.class} literals).
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static Class<?> resolveFieldType(String typeName) {
mport com.github.dropguard.summer.core.Internal;
        // JDBC reads every numeric primitive as its boxed Class (e.g. "int" and
mport com.github.dropguard.summer.core.Internal;
        // "java.lang.Integer" both -> Integer.class); this is the mapper's own
mport com.github.dropguard.summer.core.Internal;
        // domain and is intentionally not shared with codegen's raw-type table.
mport com.github.dropguard.summer.core.Internal;
        return switch (typeName) {
mport com.github.dropguard.summer.core.Internal;
            case "int", "java.lang.Integer" -> Integer.class;
mport com.github.dropguard.summer.core.Internal;
            case "long", "java.lang.Long" -> Long.class;
mport com.github.dropguard.summer.core.Internal;
            case "double", "java.lang.Double" -> Double.class;
mport com.github.dropguard.summer.core.Internal;
            case "boolean", "java.lang.Boolean" -> Boolean.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.lang.String" -> String.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.math.BigDecimal" -> java.math.BigDecimal.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.util.UUID" -> java.util.UUID.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.time.LocalDateTime" -> java.time.LocalDateTime.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.time.LocalDate" -> java.time.LocalDate.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.time.LocalTime" -> java.time.LocalTime.class;
mport com.github.dropguard.summer.core.Internal;
            case "java.time.OffsetDateTime" -> java.time.OffsetDateTime.class;
mport com.github.dropguard.summer.core.Internal;
            default ->
mport com.github.dropguard.summer.core.Internal;
                    throw new IllegalStateException(
mport com.github.dropguard.summer.core.Internal;
                            "Unsupported @RowModel field type: "
mport com.github.dropguard.summer.core.Internal;
                                    + typeName
mport com.github.dropguard.summer.core.Internal;
                                    + ". @RowModel supports JDBC-native types only (primitives,"
mport com.github.dropguard.summer.core.Internal;
                                    + " String, BigDecimal, UUID, java.time.*). Complex types such"
mport com.github.dropguard.summer.core.Internal;
                                    + " as jsonb or nested records require explicit extension.");
mport com.github.dropguard.summer.core.Internal;
        };
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
