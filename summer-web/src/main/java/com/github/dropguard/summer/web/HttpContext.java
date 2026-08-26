package com.github.dropguard.summer.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Facade for HTTP request processing. Controllers and framework handlers interact with this
 * --Response is fully encapsulated.
 *
 * <h2>Deferred Write Pattern</h2>
 *
 * <p>Summer embeds Netty and uses virtual threads for request processing. This creates two
 * execution contexts with different threading models:
 *
 * <ul>
 *   <li><b>Virtual thread</b> (request processing): Controller calls {@code ctx.ok(data)} or {@code
 *       ctx.json(status, data)} to write the response into this context.
 *   <li><b>Netty Event Loop</b> (IO): After processing completes, Netty reads from this context via
 *       {@code status()}, {@code resultObject()}, {@code body()} etc. and writes the actual HTTP
 *       response to the channel.
 * </ul>
 *
 * <p>This separation is intentional --the response is <em>deferred</em> until the IO thread is
 * ready. Controllers must explicitly set response data via the write facade methods; return values
 * from handler methods are ignored.
 *
 * @see com.github.dropguard.summer.web.server.NettyHttpServerHandler
 */
public class HttpContext {

    private static final Logger log = LoggerFactory.getLogger(HttpContext.class);

    private static final io.avaje.validation.Validator AVALIDATOR =
            io.avaje.validation.Validator.builder().build();

    private static final BodyParser DEFAULT_JSON_PARSER =
            new BodyParser(new JsonBodyConverter(), AVALIDATOR);

    private final Request request;
    private final Response response = new Response();
    private final BodyParser bodyParser;
    private ResponseState responseState = ResponseState.UNSET;

    public HttpContext(Request request) {
        this(request, DEFAULT_JSON_PARSER);
    }

    /**
     * The injected converter is the single source of truth for body parsing and response
     * serialization. No type sniffing: a {@link JsonBodyConverter} subclass is a legitimate
     * customization point (its bean is designed to be replaced/configured) and must never be
     * swapped for the static default.
     */
    public HttpContext(Request request, BodyConverter jsonConverter) {
        this(request, new BodyParser(jsonConverter, AVALIDATOR));
    }

    public HttpContext(Request request, BodyParser bodyParser) {
        this.request = request;
        this.bodyParser = bodyParser != null ? bodyParser : DEFAULT_JSON_PARSER;
    }

    // --- Read facade ---

    public Request request() {
        return request;
    }

    public HttpStatus status() {
        return response.status;
    }

    /**
     * The response body, as a <em>read-only view</em> of the internal buffer (zero-copy — no
     * defensive copy). Consumers (the IO layer, middleware wrappers) must not mutate the returned
     * array; to replace the response body, use {@link #text(HttpStatus, String)} / {@link
     * #json(HttpStatus, Object)} which install a new buffer. The middleware read-modify-write
     * pattern (read, transform, {@code ctx.text(...)} back) is safe: the original array is never
     * mutated and is dropped once replaced.
     */
    public byte[] body() {
        return response.body;
    }

    public Map<String, String> headers() {
        return java.util.Collections.unmodifiableMap(response.headers());
    }

    public Object resultObject() {
        return response.resultObject;
    }

    public BodyConverter converter() {
        return response.converter;
    }

    // --- Write facade ---
    // These methods set response data on the context (deferred write).
    // The actual IO is performed later by the Netty Event Loop thread.

    /** Sets the HTTP status code. */
    public HttpContext status(HttpStatus status) {
        response.status = status;
        return this;
    }

    /** Sets a response header (chainable). */
    public HttpContext header(String name, String value) {
        response.setHeader(name, value);
        return this;
    }

    /** Sets a response header (chainable alias for {@link #header(String, String)}). */
    public HttpContext setHeader(String name, String value) {
        response.setHeader(name, value);
        return this;
    }

    /**
     * Sets a JSON response with the given status and data object. The object will be serialized by
     * the configured {@link BodyConverter} when the response is flushed by the IO layer.
     */
    public void json(HttpStatus status, Object data) {
        BodyConverter converter = bodyParser.converter();
        response.status = status;
        response.resultObject = data;
        response.converter = converter;
        // Mutually exclusive with text(): a prior text() body must not linger, else the IO layer
        // serializes the resultObject but status from the text() call reads as a 400+DIO body.
        response.body = null;
        response.setHeader("Content-Type", converter.getContentType());
    }

    /** Sets a 200 OK JSON response. */
    public void ok(Object data) {
        json(HttpStatus.OK, data);
    }

    /** Sets a plain text response. A null body clears any previously written body. */
    public void text(HttpStatus status, String body) {
        response.status = status;
        if (body != null) {
            response.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else {
            // A null body means "no body" — drop any previously written body so the response is
            // not silently re-sent with stale content (text(400,null) after text(500,"err") must
            // send an empty 400, not the stale 500 body).
            response.body = null;
        }
        // Mutually exclusive with json(): a prior json() resultObject must not linger, else the
        // IO layer serializes the old resultObject instead of the new text body.
        response.resultObject = null;
        response.setHeader("Content-Type", "text/plain");
    }

    /**
     * Sets a 500 Internal Server Error response. Logs the full exception server-side; only a
     * generic message is sent to the client to avoid leaking implementation details.
     */
    public void error(Throwable e) {
        log.error("Request processing error", e);
        text(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
    }

    public boolean isHandled() {
        return responseState == ResponseState.HANDLED;
    }

    public void setHandled(boolean handled) {
        // setHandled(true) declares the response fully taken over outside the normal flow (the
        // WebSocket upgrade path, and the user escape hatch for channel takeover); false resets.
        this.responseState = handled ? ResponseState.HANDLED : ResponseState.UNSET;
    }

    /**
     * Marks this request as matched to a route handler. Set by the HTTP router implementations just
     * before invoking a matched handler. {@code @Internal}: consumed by the server layer to
     * distinguish "no route matched" (404) from "a matched handler wrote no response" (500).
     */
    @com.github.dropguard.summer.core.Internal
    public void markMatched() {
        this.responseState = ResponseState.MATCHED;
    }

    /** Where this request sits in the HTTP response lifecycle (see {@link ResponseState}). */
    @com.github.dropguard.summer.core.Internal
    public ResponseState responseState() {
        return responseState;
    }

    /**
     * Where a request sits in the HTTP response lifecycle. The server layer reads this after the
     * middleware/router chain completes, to answer a request that never set a status:
     *
     * <ul>
     *   <li>{@link #UNSET} — no route matched → 404 Not Found
     *   <li>{@link #MATCHED} — a handler ran but wrote no response → 500 (deferred-write contract
     *       violation)
     *   <li>{@link #HANDLED} — the response was taken over outside the normal flow (e.g. a
     *       WebSocket upgrade) → no HTTP response is sent
     * </ul>
     */
    public enum ResponseState {
        UNSET,
        MATCHED,
        HANDLED
    }

    // --- Request helpers ---

    public String path() {
        return request.getPath();
    }

    public HttpMethod method() {
        return request.getMethod();
    }

    public String getHeader(String name) {
        return request.getHeader(name);
    }

    public String header(String name) {
        return request.getHeader(name);
    }

    public String queryParam(String name) {
        return request.queryParam(name);
    }

    public String pathParam(String name) {
        return request.pathParam(name);
    }

    public <T> T body(Class<T> type) {
        return bodyParser.parse(request.getBody(), request.getContentType(), type);
    }

    public <T> T validatedBody(Class<T> type) {
        return bodyParser.parseAndValidate(request.getBody(), request.getContentType(), type);
    }

    /**
     * Flushes the buffered HTTP response to the specified {@link ResponseSink}.
     *
     * <p>Applies default status fallback (404 for unmatched, 500 for matched but no status written)
     * and delegates payload dispatching to the transport sink.
     */
    public void flushTo(ResponseSink sink) {
        if (responseState == ResponseState.HANDLED) {
            return;
        }

        if (response.status == null) {
            if (responseState == ResponseState.MATCHED) {
                log.error(
                        "Handler for {} {} matched but wrote no response — the handler must"
                                + " set a status (ctx.status/text/json/ok).",
                        request.getMethod(),
                        request.getPath());
                text(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
            } else {
                text(HttpStatus.NOT_FOUND, "Not Found");
            }
        }

        Map<String, String> headers = response.headers();

        if (response.resultObject != null && response.converter != null) {
            sink.sendObject(response.status, headers, response.resultObject, response.converter);
        } else if (response.body != null && response.body.length > 0) {
            sink.sendBytes(response.status, headers, response.body);
        } else {
            sink.sendEmpty(response.status, headers);
        }
    }
}
