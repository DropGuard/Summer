package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.web.ChunkedResponse;
import com.github.dropguard.summer.web.SseEvent;
import com.github.dropguard.summer.web.SseStream;

/** Netty-backed implementation of {@link SseStream}. */
class NettySseStream implements SseStream {

    private final ChunkedResponse response;

    public NettySseStream(ChunkedResponse response) {
        this.response = response;
        this.response.contentType("text/event-stream; charset=UTF-8");
        this.response.header("Cache-Control", "no-cache");
        this.response.header("X-Accel-Buffering", "no");
    }

    @Override
    public void send(String data) {
        send(new SseEvent(null, data, null, null, null));
    }

    @Override
    public void send(String event, String data) {
        send(new SseEvent(event, data, null, null, null));
    }

    @Override
    public void send(SseEvent event) {
        if (response.isClosed()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (event.comment() != null) {
            sb.append(": ").append(event.comment()).append("\n\n");
            response.write(sb.toString());
            return;
        }

        if (event.id() != null) {
            sb.append("id: ").append(event.id()).append("\n");
        }
        if (event.event() != null) {
            sb.append("event: ").append(event.event()).append("\n");
        }
        if (event.retry() != null) {
            sb.append("retry: ").append(event.retry()).append("\n");
        }
        if (event.data() != null) {
            String[] lines = event.data().split("\r\n|\r|\n", -1);
            for (String line : lines) {
                sb.append("data: ").append(line).append("\n");
            }
        }
        sb.append("\n");

        response.write(sb.toString());
    }

    @Override
    public void comment(String comment) {
        send(SseEvent.comment(comment));
    }

    @Override
    public boolean isClosed() {
        return response.isClosed();
    }

    @Override
    public void close() {
        response.close();
    }
}
