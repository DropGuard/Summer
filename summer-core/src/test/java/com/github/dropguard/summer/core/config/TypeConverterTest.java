package com.github.dropguard.summer.core.config;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TypeConverter}.
 *
 * <p>Tests conversion logic, null handling, and error cases.
 */
class TypeConverterTest {

    @Test
    void shouldReturnNullForNullInput() {
        assertNull(TypeConverter.convert(null, String.class));
        assertNull(TypeConverter.convert(null, Integer.class));
        assertNull(TypeConverter.convert(null, Boolean.class));
    }

    @Test
    void shouldConvertString() {
        assertEquals("hello", TypeConverter.convert("hello", String.class));
        assertEquals("", TypeConverter.convert("", String.class));
    }

    @Test
    void shouldConvertInteger() {
        assertEquals(42, TypeConverter.convert("42", Integer.class));
        assertEquals(-1, TypeConverter.convert("-1", Integer.class));
        assertEquals(0, TypeConverter.convert("0", Integer.class));
    }

    @Test
    void shouldConvertLong() {
        assertEquals(42L, TypeConverter.convert("42", Long.class));
        assertEquals(
                Long.MAX_VALUE, TypeConverter.convert(String.valueOf(Long.MAX_VALUE), Long.class));
    }

    @Test
    void shouldConvertBoolean() {
        assertTrue((Boolean) TypeConverter.convert("true", Boolean.class));
        assertTrue((Boolean) TypeConverter.convert("TRUE", Boolean.class));
        assertFalse((Boolean) TypeConverter.convert("false", Boolean.class));
        assertFalse((Boolean) TypeConverter.convert("anything", Boolean.class));
    }

    @Test
    void shouldConvertDouble() {
        assertEquals(3.14, TypeConverter.convert("3.14", Double.class));
        assertEquals(-1.0, TypeConverter.convert("-1.0", Double.class));
    }

    @Test
    void shouldThrowForUnsupportedType() {
        // Float is supported since the param-conversion unification; use a type the converter
        // genuinely cannot coerce to.
        assertThrows(
                ConfigurationException.class,
                () -> TypeConverter.convert("value", java.util.UUID.class));
    }

    @Test
    void shouldCoerceNumberToLong() {
        // The AOT config binder resolves scalar section values as Numbers (e.g. an Integer from
        // YAML) and must coerce them to the target boxed type — this is exactly the path that used
        // to throw ClassCastException: Integer cannot be cast to Long on a `long` config field.
        assertEquals(3600000L, TypeConverter.convert(Integer.valueOf(3600000), Long.class));
        assertEquals(3600000L, TypeConverter.convert(3600000L, Long.class));
    }

    @Test
    void shouldCoerceNumberToIntegerAndDouble() {
        assertEquals(42, TypeConverter.convert(Long.valueOf(42), Integer.class));
        assertEquals(3.0, TypeConverter.convert(Integer.valueOf(3), Double.class));
    }

    @Test
    void shouldThrowForInvalidInteger() {
        assertThrows(
                NumberFormatException.class, () -> TypeConverter.convert("abc", Integer.class));
    }

    @Test
    void shouldThrowForInvalidLong() {
        assertThrows(NumberFormatException.class, () -> TypeConverter.convert("abc", Long.class));
    }

    @Test
    void shouldThrowForInvalidDouble() {
        assertThrows(NumberFormatException.class, () -> TypeConverter.convert("abc", Double.class));
    }

    @Test
    void convertsEnumCaseInsensitively() {
        // The config-binding contract (Jackson ACCEPT_CASE_INSENSITIVE_ENUMS / the AOT generated
        // enumValue helper): ?env=production binds like env: production. Mixed-case constants and
        // lowercase input must both resolve — Enum.valueOf(...toUpperCase()) threw on mixed-case.
        assertEquals(Env.PRODUCTION, TypeConverter.convert("production", Env.class));
        assertEquals(Env.PRODUCTION, TypeConverter.convert("PRODUCTION", Env.class));
        assertEquals(Env.Dev, TypeConverter.convert("DEV", Env.class));
        assertEquals(Env.Dev, TypeConverter.convert("dev", Env.class));
        assertEquals(Env.Dev, TypeConverter.convert("  dev  ", Env.class), "input is trimmed");
    }

    @Test
    void throwsForUnknownEnumConstant() {
        assertThrows(
                ConfigurationException.class,
                () -> TypeConverter.convert("staging", Env.class),
                "a value matching no constant must fail loudly, not fall back");
    }

    private enum Env {
        PRODUCTION,
        Dev
    }
}
