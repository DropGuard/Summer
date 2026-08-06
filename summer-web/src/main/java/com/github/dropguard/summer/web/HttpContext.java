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
 * @see Response
 * @see com.github.dropguard.summer.web.server.NettyHttpServerHandler
 */
public class HttpContext {

    private static final Logger log = LoggerFactory.getLogger(HttpContext.class);

    private static final io.avaje.validation.Validator AVALIDATOR =
            io.avaje.validation.Validator.builder().build();

    private final Request request;
    private final Response response = new Response();
    private final BodyParser bodyParser;
    private boolean handled = false;

    public HttpContext(Request request) {
        this(request, new JsonBodyConverter());
    }

    public HttpContext(Request request, BodyConverter jsonConverter) {
        this(request, new BodyParser(jsonConverter, AVALIDATOR));
    }

    public HttpContext(Request request, BodyParser bodyParser) {
        this.request = request;
        this.bodyParser = bodyParser;
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
        return java.util.Collections.unmodifiableMap(response.headers);
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

    /** Sets a response header. */
    public HttpContext setHeader(String name, String value) {
        response.headers.put(name, value);
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
        response.headers.put("Content-Type", converter.getContentType());
    }

    /** Sets a 200 OK JSON response. */
    public void ok(Object data) {
        json(HttpStatus.OK, data);
    }

    /** Sets a plain text response. */
    public void text(HttpStatus status, String body) {
        response.status = status;
        if (body != null) {
            response.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        response.headers.put("Content-Type", "text/plain");
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
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    // --- Request helpers ---

    public String path() {
        return request.getPath();
    }

    public HttpMethod method() {
        return request.getMethod();
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
}
