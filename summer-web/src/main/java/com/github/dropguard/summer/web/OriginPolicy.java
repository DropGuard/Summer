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
        ParsedOrigin parsedOrigin = parseOrigin(origin);
        ParsedHost parsedHost = parseHost(requestHost, parsedOrigin.scheme);
        if (parsedOrigin.host == null || parsedHost.host == null) {
            return false;
        }
        return parsedOrigin.host.equalsIgnoreCase(parsedHost.host)
                && parsedOrigin.port == parsedHost.port;
    }

    private record ParsedOrigin(String scheme, String host, int port) {}

    private record ParsedHost(String host, int port) {}

    private static ParsedOrigin parseOrigin(String origin) {
        try {
            java.net.URI uri = java.net.URI.create(origin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = isSecureScheme(scheme) ? 443 : 80;
            }
            return new ParsedOrigin(scheme, host, port);
        } catch (Exception e) {
            return new ParsedOrigin(null, null, -1);
        }
    }

    private static ParsedHost parseHost(String requestHost, String originScheme) {
        try {
            // Prefix "//" so URI parses it as an authority (host[:port]) without treating leading
            // '[' (IPv6) or ':' as a scheme name.
            java.net.URI uri = java.net.URI.create("//" + requestHost);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = isSecureScheme(originScheme) ? 443 : 80;
            }
            return new ParsedHost(host, port);
        } catch (Exception e) {
            return new ParsedHost(null, -1);
        }
    }

    private static boolean isSecureScheme(String scheme) {
        return "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
    }
}
