package com.github.dropguard.summer.data.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.json.SummerObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.RecordComponentInfo;

/**
 * Discovers {@code @RowModel} records through a Jandex index and builds the {@link RowMapper}
 * instances both engines need.
 *
 * <p>Two responsibilities live here, kept as stateless entry points because they are pure functions
 * of their inputs (no shared mutable state):
 *
 * <ul>
 *   <li>{@link #scanJandex(IndexView)} — the single discovery pass; it also validates every field
 *       type up front, so an unsupported mapping fails fast at assembly rather than at row-mapping
 *       time.
 *   <li>{@link #createReflective(RowModelMeta)} — builds the runtime reflective mapper used by the
 *       Runtime DI engine when no code generation is available.
 * </ul>
 *
 * The AOT engine instead emits inline mappers at build time (see {@code WireMethodGenerator} +
 * {@code TypeReads.jdbcRead}); it reuses {@link #scanJandex(IndexView)} and the {@link
 * #resolveFieldType(String)} contract so both engines share one type truth.
 */
@Internal
public final class RowMapperFactory {

    private static final DotName ROW_MODEL_DOT =
            DotName.createSimple("com.github.dropguard.summer.data.jdbc.annotation.RowModel");

    private RowMapperFactory() {}

    /**
     * The single discovery pass: scans a Jandex index for {@code @RowModel} records and extracts
     * field metadata. Every field type is validated here via {@link #resolveFieldType(String)}, so
     * an unsupported mapping surfaces as a clear error at assembly time on both engines — not as a
     * row-mapping surprise at runtime.
     */
    public static List<RowModelMeta> scanJandex(IndexView index) {
        List<RowModelMeta> result = new ArrayList<>();
        for (ClassInfo ci : index.getKnownClasses()) {
            if (ci.isAnnotation() || ci.isInterface()) {
                continue;
            }
            if (!ci.hasAnnotation(ROW_MODEL_DOT)) {
                continue;
            }
            // recordComponents() returns components in sorted (non-declaration)
            // order in this Jandex version; the canonical constructor and therefore
            // the record's actual field order follow the declaration order. Use
            // recordComponentsInDeclarationOrder() so downstream consumers that
            // build a constructor invocation positionally (the AOT inline RowMapper)
            // stay aligned with the record's real signature. Runtime reflective
            // mapping is unaffected (it maps by name, not position).
            List<RecordComponentInfo> components = ci.recordComponentsInDeclarationOrder();
            if (components == null || components.isEmpty()) {
                continue;
            }

            List<FieldMeta> fields = new ArrayList<>();
            for (RecordComponentInfo comp : components) {
                // Validate the type up front (fail-fast, shared by both engines).
                resolveFieldType(comp.type().name().toString());
                fields.add(new FieldMeta(comp.name(), comp.type().name().toString()));
            }

            // Jandex returns null (not the annotation default) when table is unset,
            // so guard against it explicitly; an empty table fails fast at assembly.
            AnnotationValue tableValue = ci.classAnnotation(ROW_MODEL_DOT).value("table");
            String tableName = tableValue != null ? tableValue.asString() : "";
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalStateException(
                        "@RowModel on "
                                + ci.name()
                                + " must declare a non-empty table() — the physical table name is"
                                + " required.");
            }

            result.add(
                    new RowModelMeta(
                            ci.name().toString(),
                            ci.name().packagePrefix(),
                            ci.name().withoutPackagePrefix(),
                            tableName,
                            fields));
        }
        return result;
    }

    /**
     * Creates a reflective {@code RowMapper} at runtime using Jackson {@code ObjectMapper}. Used by
     * the runtime DI engine when no code generation is available.
     */
    @SuppressWarnings("unchecked")
    public static RowMapper<?> createReflective(RowModelMeta meta) {
        try {
            Class<?> modelClass = Class.forName(meta.modelClassName());
            return new ReflectiveRowMapper<>(modelClass, meta);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Cannot load @RowModel class: " + meta.modelClassName(), e);
        }
    }

    /**
     * Converts a camelCase field name to a snake_case column name, the standard SQL naming
     * convention. Example: {@code createdAt} becomes {@code created_at}.
     */
    public static String camelToSnake(String camelCase) {
        StringBuilder sb = new StringBuilder(camelCase.length() + 4);
        for (int i = 0; i < camelCase.length(); i++) {
            char ch = camelCase.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static final class ReflectiveRowMapper<T> implements RowMapper<T> {
        // Shared framework mapper: JavaTimeModule registered, unknown properties ignored
        // (extra map keys must not break record conversion), no per-module mapper drift.
        private static final ObjectMapper MAPPER = SummerObjectMapper.create();
        private final Class<T> modelClass;
        private final String[] fieldNames;
        private final String[] columnNames;
        private final Class<?>[] fieldTypes;

        ReflectiveRowMapper(Class<T> modelClass, RowModelMeta meta) {
            this.modelClass = modelClass;
            List<String> fields = new ArrayList<>();
            List<String> cols = new ArrayList<>();
            List<Class<?>> types = new ArrayList<>();
            for (FieldMeta f : meta.fields()) {
                fields.add(f.name());
                cols.add(camelToSnake(f.name()));
                types.add(resolveFieldType(f.typeName()));
            }
            this.fieldNames = fields.toArray(String[]::new);
            this.columnNames = cols.toArray(String[]::new);
            this.fieldTypes = types.toArray(Class<?>[]::new);
        }

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> values = new HashMap<>();
            for (int i = 0; i < fieldNames.length; i++) {
                values.put(fieldNames[i], rs.getObject(columnNames[i], fieldTypes[i]));
            }
            return MAPPER.convertValue(values, modelClass);
        }
    }

    /**
     * The single type contract for {@code @RowModel} fields: maps a field's type name to the Java
     * type used for JDBC's native {@code ResultSet.getObject(col, type)} read. Only JDBC-native
     * types are supported; anything else fails fast so an unsupported mapping never reaches
     * row-mapping time.
     *
     * <p>This is the one source of truth shared by the runtime reflective mapper and the AOT
     * engine's generated inline mappers (the AOT side resolves the canonical name to emit {@code
     * X.class} literals).
     */
    public static Class<?> resolveFieldType(String typeName) {
        // JDBC reads every numeric primitive as its boxed Class (e.g. "int" and
        // "java.lang.Integer" both -> Integer.class); this is the mapper's own
        // domain and is intentionally not shared with codegen's raw-type table.
        return switch (typeName) {
            case "int", "java.lang.Integer" -> Integer.class;
            case "long", "java.lang.Long" -> Long.class;
            case "double", "java.lang.Double" -> Double.class;
            case "boolean", "java.lang.Boolean" -> Boolean.class;
            case "java.lang.String" -> String.class;
            case "java.math.BigDecimal" -> java.math.BigDecimal.class;
            case "java.util.UUID" -> java.util.UUID.class;
            case "java.time.LocalDateTime" -> java.time.LocalDateTime.class;
            case "java.time.LocalDate" -> java.time.LocalDate.class;
            case "java.time.LocalTime" -> java.time.LocalTime.class;
            case "java.time.OffsetDateTime" -> java.time.OffsetDateTime.class;
            default ->
                    throw new IllegalStateException(
                            "Unsupported @RowModel field type: "
                                    + typeName
                                    + ". @RowModel supports JDBC-native types only (primitives,"
                                    + " String, BigDecimal, UUID, java.time.*). Complex types such"
                                    + " as jsonb or nested records require explicit extension.");
        };
    }
}
