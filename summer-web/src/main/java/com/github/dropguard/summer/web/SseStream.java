package com.github.dropguard.summer.web;

/**
 * Server-Sent Events (SSE) streaming facade ({@code Content-Type: text/event-stream}).
 *
 * <p>Designed for push-based text streaming such as LLM chat completions, real-time dashboards, and
 * live notification streams.
 */
public interface SseStream extends AutoCloseable {

    /** Sends a simple text data event ({@code data: <data>\n\n}). */
    void send(String data);

    /** Sends a typed event with a custom event name ({@code event: <event>\ndata: <data>\n\n}). */
    void send(String event, String data);

    /** Sends a structured {@link SseEvent}. */
    void send(SseEvent event);

    /** Sends an SSE comment line (e.g. {@code : ping\n\n}) to keep the connection alive. */
    void comment(String comment);

    /** Checks if the underlying client connection is still active and open. */
    boolean isClosed();

    /** Finishes the SSE stream and releases resources. */
    @Override
    void close();
}
