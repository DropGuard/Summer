package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.tck.negative.fixtures.data.UnsupportedNestedType;
import com.palantir.javapoet.CodeBlock;
import org.junit.jupiter.api.Test;

/**
 * Verifies the code-generation type artifacts in {@link TypeReads}: type-name to {@link
 * com.palantir.javapoet.TypeName} mapping, HTTP parse transforms, and the JDBC read expression for
 * {@code @RowModel} fields.
 */
class TypeReadsTest {

    @Test
    void jdbcReadEmitsGetObjectWithClassLiteral() {
        assertEquals(
                "rs.getObject(\"created_at\", java.time.LocalDateTime.class)",
                TypeReads.jdbcRead("created_at", "java.time.LocalDateTime").toString());
        assertEquals(
                "rs.getObject(\"id\", java.lang.Long.class)",
                TypeReads.jdbcRead("id", "java.lang.Long").toString());
        assertEquals(
                "rs.getObject(\"uuid\", java.util.UUID.class)",
                TypeReads.jdbcRead("uuid", "java.util.UUID").toString());
        assertEquals(
                "rs.getObject(\"amount\", java.math.BigDecimal.class)",
                TypeReads.jdbcRead("amount", "java.math.BigDecimal").toString());
    }

    @Test
    void jdbcReadRejectsUnsupportedTypeThroughSharedContract() {
        // The contract lives in data-jdbc's RowMapperFactory.resolveFieldType; an
        // unsupported type must fail fast here too, keeping both engines aligned.
        IllegalStateException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalStateException.class,
                        () -> TypeReads.jdbcRead("nested", UnsupportedNestedType.class.getName()));
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("Unsupported @RowModel field type"));
    }

    @Test
    void typeNameMapsPrimitivesWithoutFullyQualifiedNames() {
        assertEquals("int", TypeReads.typeName("int").toString());
        assertEquals("long", TypeReads.typeName("long").toString());
        assertEquals("java.lang.String", TypeReads.typeName("java.lang.String").toString());
    }

    @Test
    void httpParseWrapsNumericTypes() {
        CodeBlock intRead = TypeReads.httpParse("int", "ctx.request().pathParam", "id");
        // Primitives go through TypeConverter too, cast to the boxed type (auto-unboxed into the
        // declared variable) — one conversion authority, shared with the runtime resolvers.
        assertEquals(
                "(java.lang.Integer) com.github.dropguard.summer.core.config.TypeConverter.convert("
                        + "ctx.request().pathParam(\"id\"), int.class)",
                intRead.toString());

        CodeBlock strRead =
                TypeReads.httpParse("java.lang.String", "ctx.request().queryParam", "q");
        assertEquals(
                "(java.lang.String) com.github.dropguard.summer.core.config.TypeConverter.convert("
                        + "ctx.request().queryParam(\"q\"), java.lang.String.class)",
                strRead.toString());

        // Enums route through the shared TypeConverter (case-insensitive coercion), never a raw
        // String passthrough that would not compile.
        CodeBlock enumRead =
                TypeReads.httpParse("com.example.Status", "ctx.request().pathParam", "s");
        assertEquals(
                "(com.example.Status)"
                    + " com.github.dropguard.summer.core.config.TypeConverter.convert(ctx.request().pathParam(\"s\"),"
                    + " com.example.Status.class)",
                enumRead.toString());
    }
}
