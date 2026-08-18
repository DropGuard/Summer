package com.github.dropguard.summer.web.server;

import static org.mockito.Mockito.*;

import com.github.dropguard.summer.web.ChunkedResponse;
import com.github.dropguard.summer.web.SseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NettySseStreamTest {

    private ChunkedResponse chunkedResponse;
    private NettySseStream sseStream;

    @BeforeEach
    void setUp() {
        chunkedResponse = mock(ChunkedResponse.class);
        sseStream = new NettySseStream(chunkedResponse);
    }

    @Test
    void shouldSetSseHeadersOnInit() {
        verify(chunkedResponse).contentType("text/event-stream; charset=UTF-8");
        verify(chunkedResponse).header("Cache-Control", "no-cache");
        verify(chunkedResponse).header("X-Accel-Buffering", "no");
    }

    @Test
    void shouldFormatSingleLineData() {
        sseStream.send("Hello SSE");

        verify(chunkedResponse).write("data: Hello SSE\n\n");
    }

    @Test
    void shouldEscapeMultiLineData() {
        sseStream.send("Line 1\nLine 2\r\nLine 3");

        verify(chunkedResponse).write("data: Line 1\ndata: Line 2\ndata: Line 3\n\n");
    }

    @Test
    void shouldFormatCustomEvent() {
        sseStream.send("chat_delta", "token_123");

        verify(chunkedResponse).write("event: chat_delta\ndata: token_123\n\n");
    }

    @Test
    void shouldFormatStructuredEvent() {
        SseEvent event = new SseEvent("message", "payload", "id-42", 5000, null);
        sseStream.send(event);

        verify(chunkedResponse).write("id: id-42\nevent: message\nretry: 5000\ndata: payload\n\n");
    }

    @Test
    void shouldFormatComment() {
        sseStream.comment("keep-alive ping");

        verify(chunkedResponse).write(": keep-alive ping\n\n");
    }

    @Test
    void shouldForwardClose() {
        sseStream.close();

        verify(chunkedResponse).close();
    }
}
