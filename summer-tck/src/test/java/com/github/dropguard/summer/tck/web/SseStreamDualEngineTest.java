package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.ChunkedResponse;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.SseEvent;
import com.github.dropguard.summer.web.SseStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** TCK test asserting SseStream and ChunkedResponse parameter resolution on BOTH engines. */
@SummerTest
public class SseStreamDualEngineTest extends AbstractWebRouteTCK {

    public SseStreamDualEngineTest(BeanContainer context) {
        super(context);
    }

    private static class RecordingChunkedResponse implements ChunkedResponse {
        final List<String> chunks = new ArrayList<>();
        boolean closed = false;

        @Override
        public ChunkedResponse header(String name, String value) {
            return this;
        }

        @Override
        public ChunkedResponse contentType(String contentType) {
            return this;
        }

        @Override
        public ChunkedResponse status(HttpStatus status) {
            return this;
        }

        @Override
        public void write(byte[] data) {
            chunks.add(new String(data, StandardCharsets.UTF_8));
        }

        @Override
        public void write(String text) {
            chunks.add(text);
        }

        @Override
        public void flush() {}

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class RecordingSseStream implements SseStream {
        final List<String> events = new ArrayList<>();
        boolean closed = false;

        @Override
        public void send(String data) {
            events.add("data: " + data + "\n\n");
        }

        @Override
        public void send(String event, String data) {
            events.add("event: " + event + "\ndata: " + data + "\n\n");
        }

        @Override
        public void send(SseEvent event) {
            events.add(event.toString());
        }

        @Override
        public void comment(String comment) {
            events.add(": " + comment + "\n\n");
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @DualEngine
    protected void sseStreamResolvesAndDispatchesOnBothEngines() throws Exception {
        Request req = new Request(HttpMethod.GET, "/rt/chat/stream", null, null, null);
        RecordingSseStream sse = new RecordingSseStream();
        req.setAttribute(RequestAttributes.SSE_STREAM, sse);

        HttpContext ctx = new HttpContext(req);
        router.route(ctx);

        assertTrue(ctx.isHandled(), "SSE route should mark context as handled");
        assertEquals(3, sse.events.size());
        assertEquals("data: token:1\n\n", sse.events.get(0));
        assertEquals("event: delta\ndata: token:2\n\n", sse.events.get(1));
        assertEquals("data: [DONE]\n\n", sse.events.get(2));
        assertTrue(sse.closed, "SSE stream should be closed by controller");
    }

    @DualEngine
    protected void chunkedResponseResolvesAndDispatchesOnBothEngines() throws Exception {
        Request req = new Request(HttpMethod.GET, "/rt/export/chunked", null, null, null);
        RecordingChunkedResponse chunked = new RecordingChunkedResponse();
        req.setAttribute(RequestAttributes.CHUNKED_RESPONSE, chunked);

        HttpContext ctx = new HttpContext(req);
        router.route(ctx);

        assertTrue(ctx.isHandled(), "Chunked route should mark context as handled");
        assertEquals(2, chunked.chunks.size());
        assertEquals("chunk-A\n", chunked.chunks.get(0));
        assertEquals("chunk-B\n", chunked.chunks.get(1));
        assertTrue(chunked.closed, "Chunked response should be closed by controller");
    }
}
