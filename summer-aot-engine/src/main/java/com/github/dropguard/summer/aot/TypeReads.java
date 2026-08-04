package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import org.jboss.jandex.Type;

/**
 * Single source of truth for the code-generation artifacts the AOT engine needs from a type name: a
 * JavaPoet {@link TypeName} for declarations, an HTTP read transform (parse/valueOf) for converting
 * a String request value into the target type, and a JDBC read expression ({@code rs.getObject(col,
 * X.class)}) for mapping a {@code @RowModel} field.
 *
 * <p>The primitive type mapping (the part previously duplicated across these generators) now lives
 * once in {@link PrimitiveTypes}; this class derives its {@link TypeName} from that single table.
 * HTTP String-to-value parsing ({@link #httpParse}) and JDBC column types stay here / in {@code
 * RowMapperFactory} because they are engine-specific dimensions, not the same mapping: codegen
 * wants the raw primitive declaration ({@code int}), while JDBC reads every numeric type as its
 * boxed {@link Class} ({@code Integer.class}).
 *
 * <p>The JDBC type contract itself ({@code type name -> Class<?>>}) stays in {@code
 * com.github.dropguard.summer.data.jdbc.RowMapperFactory#resolveFieldType} — it is a runtime
 * dependency of the reflective mapper, so it cannot move into this engine without inverting the
 * dependency graph. The AOT side reuses that single contract via {@link #jdbcRead} and resolves the
 * canonical name to emit {@code X.class} literals.
 */
@Internal
public final class TypeReads {

    private TypeReads() {}

    /**
     * Maps a fully-qualified (or primitive) type name to a JavaPoet {@link TypeName}. Primitive
     * type names resolve through the shared {@link PrimitiveTypes} table to their raw type (so
     * {@code "int"} yields {@code int}, not {@code java.lang.Integer}); anything else is treated as
     * a reference type and best-guessed.
     */
    public static TypeName typeName(String typeName) {
        Class<?> raw = PrimitiveTypes.rawType(typeName);
        if (raw != null) return TypeName.get(raw);
        return com.palantir.javapoet.ClassName.bestGuess(typeName);
    }

    /** Maps a Jandex {@link Type} to a JavaPoet {@link TypeName}. */
    public static TypeName typeName(Type type) {
        return typeName(type.name().toString());
    }

    /**
     * Builds an expression that reads a request parameter named {@code paramName} via {@code
     * reader} (e.g. {@code ctx.request().pathParam}) and converts it from String to the target type
     * for primitive/boxed numeric and boolean types. Reference types (String, records, ...) are
     * passed through unchanged.
     */
    public static CodeBlock httpParse(String typeName, String reader, String paramName) {
        CodeBlock raw = CodeBlock.of("$L($S)", reader, paramName);
        return switch (typeName) {
            case "int", "java.lang.Integer" -> CodeBlock.of("java.lang.Integer.parseInt($L)", raw);
            case "long", "java.lang.Long" -> CodeBlock.of("java.lang.Long.parseLong($L)", raw);
            case "double", "java.lang.Double" ->
                    CodeBlock.of("java.lang.Double.parseDouble($L)", raw);
            case "float", "java.lang.Float" -> CodeBlock.of("java.lang.Float.parseFloat($L)", raw);
            case "short", "java.lang.Short" -> CodeBlock.of("java.lang.Short.parseShort($L)", raw);
            case "byte", "java.lang.Byte" -> CodeBlock.of("java.lang.Byte.parseByte($L)", raw);
            case "boolean", "java.lang.Boolean" ->
                    CodeBlock.of("java.lang.Boolean.parseBoolean($L)", raw);
            case "char", "java.lang.Character" ->
                    CodeBlock.of("$T.valueOf($L)", Character.class, raw);
            default -> raw;
        };
    }

    /**
     * Builds the JDBC-native read expression for a {@code @RowModel} field, e.g. {@code
     * rs.getObject("created_at", LocalDateTime.class)}. Reuses the {@code
     * RowMapperFactory#resolveFieldType} contract so the generated mapper stays aligned with the
     * runtime reflective mapper (same type truth, no Jackson fallback).
     */
    public static CodeBlock jdbcRead(String columnName, String typeName) {
        String canonical =
                com.github.dropguard.summer.data.jdbc.RowMapperFactory.resolveFieldType(typeName)
                        .getCanonicalName();
        return CodeBlock.of("rs.getObject($S, $L.class)", columnName, canonical);
    }
}
