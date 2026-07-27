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
        assertThrows(
                ConfigurationException.class, () -> TypeConverter.convert("value", Float.class));
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
}
