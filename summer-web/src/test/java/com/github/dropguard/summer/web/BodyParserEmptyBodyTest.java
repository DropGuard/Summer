package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.dropguard.summer.web.exception.BodyParseException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * A handler that declares a body parameter requires a body: an empty body must be a 400 decided by
 * the pipeline itself, uniformly for every {@link BodyConverter} — the Avaje SPI used to return
 * null here, which skipped validation and failed downstream as a 500.
 */
class BodyParserEmptyBodyTest {

    @Test
    void emptyBodyIsRejectedBeforeTheConverterIsConsulted() throws IOException {
        BodyConverter converter = mock(BodyConverter.class);
        BodyParser parser = new BodyParser(converter, null);

        assertThrows(
                BodyParseException.class,
                () -> parser.parse(new byte[0], "application/json", String.class));
        assertThrows(
                BodyParseException.class,
                () -> parser.parse(null, "application/json", String.class));

        verify(converter, never()).read(any(), any());
    }
}
