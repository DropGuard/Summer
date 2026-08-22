package com.github.dropguard.summer.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents an HTTP request. */
public class Request {
    private static final Logger log = LoggerFactory.getLogger(Request.class);

    private static final byte[] EMPTY_BODY = new byte[0];

    private final HttpMethod method;
    private final String path;
    private final String query;
    private byte[] body;
    private java.util.function.Supplier<byte[]> lazyBody;
    private final String contentType;
    private final Map<String, String> headers;
    private Map<String, Object> attributes;

    public Request(HttpMethod method, String path, String query, String contentType, byte[] body) {
        this(method, path, query, contentType, body, null);
    }

    public Request(
            HttpMethod method,
            String path,
            String query,
            String contentType,
            byte[] body,
            Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.body = body != null ? body : EMPTY_BODY;
        this.contentType = contentType;
        this.headers = headers != null ? headers : Collections.emptyMap();
    }

    /**
     * Lazy-body constructor: the body byte array is not materialized until {@link #getBody()} is
     * first called. This avoids a per-request heap allocation for HTTP methods that never read the
     * body (GET, DELETE, HEAD).
     */
    public Request(
            HttpMethod method,
            String path,
            String query,
            String contentType,
            java.util.function.Supplier<byte[]> lazyBody,
            Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.lazyBody = lazyBody;
        this.contentType = contentType;
        this.headers = headers != null ? headers : Collections.emptyMap();
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public byte[] getBody() {
        if (body == null && lazyBody != null) {
            body = lazyBody.get();
            lazyBody = null; // release the Supplier (and its ByteBuf closure) after first read
        }
        return body != null ? body : EMPTY_BODY;
    }

    public String getContentType() {
        return contentType;
    }

    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        String val = headers.get(name);
        if (val != null) {
            return val;
        }
        return headers.get(name.toLowerCase());
    }

    public boolean isGet() {
        return HttpMethod.GET == method;
    }

    public boolean isPost() {
        return HttpMethod.POST == method;
    }

    public boolean isPut() {
        return HttpMethod.PUT == method;
    }

    public boolean isDelete() {
        return HttpMethod.DELETE == method;
    }

    @Override
    public String toString() {
        return "Request{"
                + "method='"
                + method
                + '\''
                + ", path='"
                + path
                + '\''
                + ", query='"
                + query
                + '\''
                + '}';
    }

    public <T> void setAttribute(RequestAttributes.AttributeKey<T> key, T value) {
        if (attributes == null) {
            attributes = new HashMap<>(4);
        }
        attributes.put(key.name(), value);
    }

    public void setPathParam(String name, String value) {
        if (attributes == null) {
            attributes = new HashMap<>(4);
        }
        attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(RequestAttributes.AttributeKey<T> key) {
        if (attributes == null) {
            return null;
        }
        Object val = attributes.get(key.name());
        if (val instanceof java.util.function.Supplier<?> supplier) {
            val = supplier.get();
            attributes.put(key.name(), val);
        }
        return (T) val;
    }

    public <T> void setLazyAttribute(
            RequestAttributes.AttributeKey<T> key, java.util.function.Supplier<T> supplier) {
        if (attributes == null) {
            attributes = new HashMap<>(4);
        }
        attributes.put(key.name(), supplier);
    }

    /**
     * All request attributes, as an immutable view. Mutations go through the explicit write surface
     * ({@link #setAttribute} / {@link #setPathParam}) — the returned map is a zero-copy
     * unmodifiable wrapper, so callers cannot corrupt internal state.
     */
    public Map<String, Object> getAttributes() {
        return attributes != null
                ? Collections.unmodifiableMap(attributes)
                : Collections.emptyMap();
    }

    // Query parameters — lazily parsed and cached
    private Map<String, String> queryParams;

    /**
     * Parses and caches the query string once (subsequent calls return the cached map).
     *
     * <p>Duplicate keys are <em>first-wins</em>, matching the servlet {@code getParameter}
     * convention. Unparseable percent-encodings fall back to the raw value (lenient, same as
     * Spring's {@code UrlUtils}).
     */
    public Map<String, String> getQueryParameters() {
        if (queryParams != null) return queryParams;
        queryParams = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) {
                    continue; // empty segment from "&&" or a leading "&" — no key/value
                }
                int eqIndex = pair.indexOf('=');
                String name, value;
                if (eqIndex != -1) {
                    name = pair.substring(0, eqIndex);
                    value = pair.substring(eqIndex + 1);
                } else {
                    name = pair;
                    value = "";
                }
                queryParams.putIfAbsent(decodeParam(name), decodeParam(value));
            }
        }
        return queryParams;
    }

    private static String decodeParam(String raw) {
        try {
            return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Malformed percent-encoding (e.g. %zz) — fall back to the raw value.
            // Lenient by design: query strings are untrusted input and a decode failure
            // must not abort request handling.
            log.debug("[Summer] Malformed query parameter encoding: '{}' — using raw value", raw);
            return raw;
        }
    }

    // --- Explicit Parameter Extraction APIs ---

    /**
     * Extracts a path parameter by name.
     *
     * <p>Warning: The returned value is URL-decoded. If outputting to HTML, you must escape it to
     * prevent XSS. For JSON responses, Jackson handles escaping automatically.
     */
    public String pathParam(String name) {
        if (attributes == null) {
            return null;
        }
        Object val = attributes.get(name);
        return val != null ? val.toString() : null;
    }

    /**
     * Extracts a query parameter by name.
     *
     * <p>Warning: The returned value is URL-decoded. If outputting to HTML, you must escape it to
     * prevent XSS. For JSON responses, Jackson handles escaping automatically.
     */
    public String queryParam(String name) {
        return getQueryParameters().get(name);
    }
}
