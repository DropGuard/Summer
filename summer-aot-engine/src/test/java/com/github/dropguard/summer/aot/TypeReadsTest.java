package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.tck.negative.fixtures.data.errors.UnsupportedNestedType;
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
        assertEquals(
                "java.lang.Integer.parseInt(ctx.request().pathParam(\"id\"))", intRead.toString());

        CodeBlock strRead =
                TypeReads.httpParse("java.lang.String", "ctx.request().queryParam", "q");
        assertEquals("ctx.request().queryParam(\"q\")", strRead.toString());
    }
}
