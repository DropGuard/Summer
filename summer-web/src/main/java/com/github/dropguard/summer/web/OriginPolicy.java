package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import java.util.List;

/**
 * Single source of truth for cross-origin allowance decisions, shared by the HTTP CORS layer
 * ({@code CorsMiddleware}) and the WebSocket upgrade guard ({@code ServerOriginChecker}) so the two
 * never drift into inconsistent matching.
 *
 * <p>Matching rules, in priority order:
 *
 * <ul>
 *   <li>An explicit {@code "*"} entry allows any origin.
 *   <li>Otherwise, the request origin is compared against the allowed entries. A literal match
 *       (scheme + host + port) is required.
 *   <li>If the allowed list is empty, the request is allowed only when its origin is same-origin
 *       with the request host (host and port equal). This is the safe default for single-origin
 *       deployments that do not configure cross-origin access.
 * </ul>
 *
 * <p>The policy is pure and side-effect free; callers decide how to surface the result (a boolean
 * for WebSocket upgrades, or reflecting the matched origin back into a {@code
 * Access-Control-Allow-Origin} header for CORS).
 */
@Internal
public final class OriginPolicy {

    private OriginPolicy() {}

    /**
     * Decides whether {@code origin} may access a resource served for {@code requestHost}.
     *
     * @param origin the {@code Origin} request header (may be null)
     * @param allowed the configured allowed origins (each may be {@code "*"} or a full origin such
     *     as {@code https://app.example.com:8443}); null or empty means same-origin-only
     * @param requestHost the {@code Host} header value (host, optionally {@code :port}); may be
     *     null
     * @return true if the origin is allowed
     */
    public static boolean isAllowed(String origin, List<String> allowed, String requestHost) {
        if (allowed == null || allowed.isEmpty()) {
            return isSameOrigin(origin, requestHost);
        }
        if (allowed.stream().anyMatch("*"::equals)) {
            return true;
        }
        if (origin == null) {
            return false;
        }
        return allowed.stream().anyMatch(origin::equals);
    }

    /** True when {@code origin} resolves to the same host and port as {@code requestHost}. */
    static boolean isSameOrigin(String origin, String requestHost) {
        if (origin == null || requestHost == null) {
            return false;
        }
        String originHost = hostOf(origin);
        int originPort = portOf(origin);
        String reqHost = hostOf(requestHost);
        int reqPort = portOf(requestHost);
        if (originHost == null || reqHost == null) {
            return false;
        }
        return originHost.equals(reqHost) && originPort == reqPort;
    }

    private static String hostOf(String value) {
        java.net.URI uri = java.net.URI.create(value);
        // In Java 25+, URI.create("localhost:8080") produces an opaque URI
        // (scheme=localhost, host=null) instead of throwing — check isOpaque()
        // rather than relying on a catch that never fires.
        if (!uri.isOpaque()) {
            String host = uri.getHost();
            if (host != null) return host;
        }
        int colon = value.lastIndexOf(':');
        return colon < 0 ? value : value.substring(0, colon);
    }

    private static int portOf(String value) {
        java.net.URI uri = java.net.URI.create(value);
        if (!uri.isOpaque()) {
            if (uri.getPort() != -1) return uri.getPort();
            return "https".equals(uri.getScheme()) ? 443 : 80;
        }
        int colon = value.lastIndexOf(':');
        if (colon < 0) {
            return 80;
        }
        try {
            return Integer.parseInt(value.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Malformed origin (unparsable port): '" + value + "'", e);
        }
    }
}
