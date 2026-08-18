package com.github.dropguard.summer.web;

/**
 * An individual Server-Sent Event (SSE) message.
 *
 * @param event optional event type name (e.g. "message", "delta")
 * @param data the data payload
 * @param id optional event ID for client reconnect tracking
 * @param retry optional reconnection retry time in milliseconds
 * @param comment optional comment line (e.g. for keep-alive ping)
 */
public record SseEvent(String event, String data, String id, Integer retry, String comment) {

    public static SseEvent of(String data) {
        return new SseEvent(null, data, null, null, null);
    }

    public static SseEvent of(String event, String data) {
        return new SseEvent(event, data, null, null, null);
    }

    public static SseEvent comment(String comment) {
        return new SseEvent(null, null, null, null, comment);
    }
}
