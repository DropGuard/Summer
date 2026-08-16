package com.github.dropguard.summer.web;

/**
 * Represents a request handler that processes HTTP requests.
 *
 * <p>Handlers are invoked by the router during request processing. The return value of {@link
 * #handle(HttpContext)} is <b>not</b> used by the framework -- controllers must write responses
 * explicitly via {@link HttpContext} methods (e.g., {@code ctx.ok(data)}, {@code ctx.json(status,
 * data)}).
 *
 * <p>This is by design: Summer uses a deferred write pattern where the response is written to the
 * context during request processing, then flushed to the network by the IO layer.
 *
 * <p>The signature declares {@code throws Exception}: a handler may let a checked exception escape
 * to the framework, where {@code @ExceptionHandler} maps it to a response — the Java rendering of
 * Gin's panic → recovery-middleware model (checked exceptions are Java's own dimension; the
 * no-throws shape was a Go-transplant artifact and would force every handler to catch-and-wrap).
 *
 * @see HttpContext
 */
@FunctionalInterface
public interface Handler {
    /**
     * Handles an HTTP request. Controllers must write responses explicitly via {@link HttpContext}
     * methods (e.g., {@code ctx.ok(data)}, {@code ctx.text(status, body)}). May throw; the
     * framework maps the exception through the registered {@code @ExceptionHandler}s.
     */
    void handle(HttpContext ctx) throws Exception;
}
