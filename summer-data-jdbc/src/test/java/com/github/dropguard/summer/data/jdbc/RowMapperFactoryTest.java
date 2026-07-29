package com.github.dropguard.summer.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.tck.negative.fixtures.data.UnsupportedNestedType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RowMapperFactory#resolveFieldType(String)} — the single type contract
 * shared by the runtime reflective mapper and the AOT engine's generated inline mappers. {@link
 * RowMapperFactory#scanJandex(org.jboss.jandex.IndexView)} validates every field through this
 * method, so an unsupported mapping fails fast at assembly rather than as a row-mapping surprise at
 * runtime.
 */
class RowMapperFactoryTest {

    @Test
    void acceptsJdbcNativeTypes() {
        assertSame(Long.class, RowMapperFactory.resolveFieldType("java.lang.Long"));
        assertSame(String.class, RowMapperFactory.resolveFieldType("java.lang.String"));
        assertSame(BigDecimal.class, RowMapperFactory.resolveFieldType("java.math.BigDecimal"));
        assertSame(UUID.class, RowMapperFactory.resolveFieldType("java.util.UUID"));
        assertSame(
                LocalDateTime.class, RowMapperFactory.resolveFieldType("java.time.LocalDateTime"));
        assertSame(Integer.class, RowMapperFactory.resolveFieldType("int"));
    }

    @Test
    void rejectsCollectionField() {
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RowMapperFactory.resolveFieldType(
                                        "java.util.List<java.lang.String>"));
        assertTrue(ex.getMessage().contains("Unsupported @RowModel field type"));
    }

    @Test
    void rejectsNestedRecordField() {
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RowMapperFactory.resolveFieldType(
                                        UnsupportedNestedType.class.getName()));
        assertTrue(ex.getMessage().contains("Unsupported @RowModel field type"));
    }

    @Test
    void acceptsSupportedTypesWithoutThrowing() {
        assertDoesNotThrow(() -> RowMapperFactory.resolveFieldType("java.lang.Long"));
        assertDoesNotThrow(() -> RowMapperFactory.resolveFieldType("java.time.LocalDate"));
    }
}
