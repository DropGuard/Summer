package com.github.dropguard.summer.web;

/**
 * Low-level HTTP chunked transfer response stream ({@code Transfer-Encoding: chunked}).
 *
 * <p>Enables streaming responses of indeterminate length with zero buffering, such as large file
 * exports, real-time metrics, or live data feeds.
 */
public interface ChunkedResponse extends AutoCloseable {

    /** Sets a response header before the initial chunk is transmitted. */
    ChunkedResponse header(String name, String value);

    /** Sets the response Content-Type header. */
    ChunkedResponse contentType(String contentType);

    /** Sets the HTTP status code (defaults to 200 OK). */
    ChunkedResponse status(HttpStatus status);

    /** Writes raw bytes as an HTTP chunk. */
    void write(byte[] data);

    /** Writes a UTF-8 string as an HTTP chunk. */
    void write(String text);

    /** Flushes any buffered chunks to the network. */
    void flush();

    /** Checks if the underlying client connection is still active and open. */
    boolean isClosed();

    /**
     * Finishes the chunked response by sending the terminal zero-byte chunk ({@code
     * LastHttpContent}).
     */
    @Override
    void close();
}
