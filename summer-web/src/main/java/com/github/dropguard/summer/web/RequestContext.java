package com.github.dropguard.summer.web;

/**
 * The request-scoped context for the currently executing request.
 *
 * <p>Summer's Netty server runs each request on its own virtual thread and processes the entire
 * middleware + controller + (AOP) service chain synchronously within it. Because of that
 * single-threaded-per-request execution, a static holder ({@link RequestContextHolder}) can expose
 * the active request to any layer — including service beans invoked through AOP interceptors —
 * without the caller having to thread a user id through every method signature (the Gin-style "pass
 * it down" pattern).
 *
 * <p>The context is produced by the auth middleware the moment a request is authenticated (see
 * {@code AuthMiddleware}) and cleared by the server entry point in a {@code finally} block ({@code
 * NettyHttpServerHandler}). The user id is resolved eagerly at construction time, so a
 * fully-populated context is visible to every downstream layer.
 */
public final class RequestContext {

    private final Request request;
    private final Long userId;

    public RequestContext(Request request, Long userId) {
        this.request = request;
        this.userId = userId;
    }

    /**
     * Creates a userId-only context for non-HTTP flows (tests, scheduled tasks, background workers)
     * via {@link RequestContextHolder#set(Long)}. There is no HTTP request here, so the
     * request-backed accessors ({@link #request()}, {@link #attributes()}, {@link #attribute}) fail
     * loudly rather than fabricating or silently degrading to an empty request.
     */
    public RequestContext(Long userId) {
        this.request = null;
        this.userId = userId;
    }

    /** The underlying HTTP request, or a loud error when the context is userId-only. */
    public Request request() {
        if (request == null) {
            throw new IllegalStateException(
                    "No HTTP request in this userId-only RequestContext — created via"
                            + " RequestContext(Long) for non-HTTP flows (tests, scheduled tasks)."
                            + " Request-backed APIs are unavailable here.");
        }
        return request;
    }

    /** The authenticated user id, or {@code null} when the request is unauthenticated. */
    public Long userId() {
        return userId;
    }

    /** All request attributes (typed keys available via {@link RequestAttributes}). */
    public java.util.Map<String, Object> attributes() {
        return request().getAttributes();
    }

    @SuppressWarnings("unchecked")
    public <T> T attribute(RequestAttributes.AttributeKey<T> key) {
        return request().getAttribute(key);
    }
}
