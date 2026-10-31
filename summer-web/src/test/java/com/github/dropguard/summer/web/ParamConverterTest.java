package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.web.exception.BadRequestException;
import org.junit.jupiter.api.Test;

/**
 * Path/query parameter conversion failures are CLIENT errors: a value the caller formatted wrongly
 * must answer 400 (BadRequestException naming the parameter), not surface as a raw
 * NumberFormatException that the exception mapper treats as an unmapped 500.
 */
class ParamConverterTest {

    enum Color {
        RED,
        BLUE
    }

    @Test
    void numericMismatchIsABadRequest() {
        BadRequestException e =
                assertThrows(
                        BadRequestException.class,
                        () -> ParamConverter.convert("abc", Integer.class, "id", "Path"));
        assertEquals("Path parameter 'id' must be a Integer but was 'abc'", e.getMessage());
    }

    @Test
    void enumMismatchIsABadRequest() {
        assertThrows(
                BadRequestException.class,
                () -> ParamConverter.convert("GREEN", Color.class, "color", "Query"));
    }

    @Test
    void validValuesStillConvert() {
        assertEquals(42, ParamConverter.convert("42", Integer.class, "id", "Path"));
        assertEquals(Color.RED, ParamConverter.convert("RED", Color.class, "color", "Query"));
    }
}
