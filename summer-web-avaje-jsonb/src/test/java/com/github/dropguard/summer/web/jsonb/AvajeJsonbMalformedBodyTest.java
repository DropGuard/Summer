package com.github.dropguard.summer.web.jsonb;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Malformed JSON under the Avaje converter is a CLIENT error: the converter surfaces Avaje's
 * unchecked {@code JsonbException} as IOException so the body pipeline maps it to {@code
 * BodyParseException} (400) — matching the default Jackson converter — and an empty body is
 * rejected by the pipeline itself for every converter, instead of returning null that skipped
 * validation and failed downstream as a 500.
 */
class AvajeJsonbMalformedBodyTest {

    @Test
    void malformedJsonIsAnIOExceptionNotARuntimeEscape() {
        AvajeJsonbBodyConverter converter = new AvajeJsonbBodyConverter();
        IOException e =
                assertThrows(
                        IOException.class,
                        () ->
                                converter.read(
                                        "{not json".getBytes(StandardCharsets.UTF_8),
                                        PersonDto.class));
        assertTrue(e.getMessage().contains("PersonDto"));
    }
}
