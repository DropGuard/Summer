package com.github.dropguard.summer.web;

import java.util.Map;

/**
 * Sink for writing finalized HTTP responses to the underlying server transport (e.g. Netty).
 *
 * <p>Separates response buffering in {@link HttpContext} from physical transport writes.
 */
public interface ResponseSink {

    /**
     * Sends a pre-serialized binary response body (e.g. text, HTML, raw bytes).
     *
     * @param status the HTTP status code
     * @param headers the response headers
     * @param body the response body bytes
     */
    void sendBytes(HttpStatus status, Map<String, String> headers, byte[] body);

    /**
     * Sends an object to be serialized by the given converter.
     *
     * @param status the HTTP status code
     * @param headers the response headers
     * @param resultObject the object to serialize
     * @param converter the body converter
     */
    void sendObject(
            HttpStatus status,
            Map<String, String> headers,
            Object resultObject,
            BodyConverter converter);

    /**
     * Sends an empty response body (e.g. 204 No Content, DELETE 200 OK without body).
     *
     * @param status the HTTP status code
     * @param headers the response headers
     */
    void sendEmpty(HttpStatus status, Map<String, String> headers);
}
