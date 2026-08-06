package com.github.dropguard.summer.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents an HTTP request. */
public class Request {
    private static final Logger log = LoggerFactory.getLogger(Request.class);

    private final HttpMethod method;
    private final String path;
    private final byte[] rawPathBytes;
    private final String query;
    private final byte[] body;
    private final String contentType;
    private final Map<String, String> headers;
    private final Map<String, Object> attributes = new HashMap<>();

    public Request(HttpMethod method, String path, String query, String contentType, byte[] body) {
        this(
                method,
                path,
                query,
                contentType,
                body,
                new HashMap<>(),
                path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public Request(
            HttpMethod method,
            String path,
            String query,
            String contentType,
            byte[] body,
            Map<String, String> headers,
            byte[] rawPathBytes) {
        this.method = method;
        this.path = path;
        this.rawPathBytes = rawPathBytes;
        this.query = query;
        this.body = body != null ? body : new byte[0];
        this.contentType = contentType;
        this.headers = headers != null ? headers : new HashMap<>();
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /**
     * The raw (undecoded) request path bytes, for zero-allocation radix-trie matching.
     *
     * <p>{@code @Internal}: an engine-level optimization channel used by {@code
     * RadixTreeHttpRouter} to match against the exact bytes on the wire — the public API surface is
     * {@link #path()} (decoded {@code String}). Kept public because the router lives in a different
     * module/package. The returned array is a reference to internal state and must not be mutated.
     */
    @com.github.dropguard.summer.core.Internal
    public byte[] getRawPathBytes() {
        return rawPathBytes;
    }

    public String getQuery() {
        return query;
    }

    public byte[] getBody() {
        return body;
    }

    public String getContentType() {
        return contentType;
    }

    public String getHeader(String name) {
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
        attributes.put(key.name(), value);
    }

    public void setPathParam(String name, String value) {
        attributes.put(name, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(RequestAttributes.AttributeKey<T> key) {
        return (T) attributes.get(key.name());
    }

    /**
     * All request attributes, as an immutable view. Mutations go through the explicit write surface
     * ({@link #setAttribute} / {@link #setPathParam}) — the returned map is a zero-copy
     * unmodifiable wrapper, so callers cannot corrupt internal state.
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
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
        return (String) attributes.get(name);
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
