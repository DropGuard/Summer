package com.github.dropguard.summer.web.server;

import io.netty.handler.codec.http.HttpHeaders;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A zero-copy, read-only {@link java.util.Map} view over Netty's {@link HttpHeaders}.
 *
 * <p>Instead of eagerly copying every header into a {@code HashMap} per request, this wrapper
 * delegates lookups directly to Netty's case-insensitive header storage. The backing {@code
 * FullHttpRequest} is retained for the lifetime of the virtual thread, so the headers are
 * guaranteed to be valid for the entire request processing lifecycle.
 *
 * <p>{@link #entrySet()} materializes only when explicitly iterated (e.g. by middleware that needs
 * to enumerate all headers); the common path ({@link #get}) is a zero-allocation delegation.
 */
class NettyHeadersMap extends AbstractMap<String, String> {

    private final HttpHeaders nettyHeaders;

    NettyHeadersMap(HttpHeaders nettyHeaders) {
        this.nettyHeaders = nettyHeaders;
    }

    @Override
    public String get(Object key) {
        if (key instanceof String name) {
            // Netty's HttpHeaders.get() is already case-insensitive.
            return nettyHeaders.get(name);
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String name) {
            return nettyHeaders.contains(name);
        }
        return false;
    }

    @Override
    public int size() {
        return nettyHeaders.size();
    }

    @Override
    public boolean isEmpty() {
        return nettyHeaders.isEmpty();
    }

    /**
     * Lazily materializes the header entries. Only called when middleware or user code iterates all
     * headers — the hot path ({@code getHeader("Authorization")}) never triggers this.
     */
    @Override
    public Set<Entry<String, String>> entrySet() {
        var set = new LinkedHashSet<Entry<String, String>>();
        for (var entry : nettyHeaders) {
            set.add(Map.entry(entry.getKey().toLowerCase(), entry.getValue()));
        }
        return set;
    }
}
