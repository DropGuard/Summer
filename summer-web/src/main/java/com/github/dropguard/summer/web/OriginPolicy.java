mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Single source of truth for cross-origin allowance decisions, shared by the HTTP CORS layer
mport com.github.dropguard.summer.core.Internal;
 * ({@code CorsMiddleware}) and the WebSocket upgrade guard ({@code ServerOriginChecker}) so the two
mport com.github.dropguard.summer.core.Internal;
 * never drift into inconsistent matching.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Matching rules, in priority order:
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <ul>
@Internal
mport com.github.dropguard.summer.core.Internal;
 *   <li>An explicit {@code "*"} entry allows any origin.
mport com.github.dropguard.summer.core.Internal;
 *   <li>Otherwise, the request origin is compared against the allowed entries. A literal match
mport com.github.dropguard.summer.core.Internal;
 *       (scheme + host + port) is required.
mport com.github.dropguard.summer.core.Internal;
 *   <li>If the allowed list is empty, the request is allowed only when its origin is same-origin
mport com.github.dropguard.summer.core.Internal;
 *       with the request host (host and port equal). This is the safe default for single-origin
mport com.github.dropguard.summer.core.Internal;
 *       deployments that do not configure cross-origin access.
mport com.github.dropguard.summer.core.Internal;
 * </ul>
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>The policy is pure and side-effect free; callers decide how to surface the result (a boolean
mport com.github.dropguard.summer.core.Internal;
 * for WebSocket upgrades, or reflecting the matched origin back into a {@code
mport com.github.dropguard.summer.core.Internal;
 * Access-Control-Allow-Origin} header for CORS).
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class OriginPolicy {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private OriginPolicy() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Decides whether {@code origin} may access a resource served for {@code requestHost}.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param origin the {@code Origin} request header (may be null)
mport com.github.dropguard.summer.core.Internal;
     * @param allowed the configured allowed origins (each may be {@code "*"} or a full origin such
mport com.github.dropguard.summer.core.Internal;
     *     as {@code https://app.example.com:8443}); null or empty means same-origin-only
mport com.github.dropguard.summer.core.Internal;
     * @param requestHost the {@code Host} header value (host, optionally {@code :port}); may be
mport com.github.dropguard.summer.core.Internal;
     *     null
mport com.github.dropguard.summer.core.Internal;
     * @return true if the origin is allowed
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public static boolean isAllowed(String origin, List<String> allowed, String requestHost) {
mport com.github.dropguard.summer.core.Internal;
        if (allowed == null || allowed.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return isSameOrigin(origin, requestHost);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (allowed.stream().anyMatch("*"::equals)) {
mport com.github.dropguard.summer.core.Internal;
            return true;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (origin == null) {
mport com.github.dropguard.summer.core.Internal;
            return false;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return allowed.stream().anyMatch(origin::equals);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** True when {@code origin} resolves to the same host and port as {@code requestHost}. */
mport com.github.dropguard.summer.core.Internal;
    static boolean isSameOrigin(String origin, String requestHost) {
mport com.github.dropguard.summer.core.Internal;
        if (origin == null || requestHost == null) {
mport com.github.dropguard.summer.core.Internal;
            return false;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        String originHost = hostOf(origin);
mport com.github.dropguard.summer.core.Internal;
        int originPort = portOf(origin, originHost);
mport com.github.dropguard.summer.core.Internal;
        String reqHost = hostOf(requestHost);
mport com.github.dropguard.summer.core.Internal;
        int reqPort = portOf(requestHost, reqHost);
mport com.github.dropguard.summer.core.Internal;
        if (originHost == null || reqHost == null) {
mport com.github.dropguard.summer.core.Internal;
            return false;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return originHost.equals(reqHost) && originPort == reqPort;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static String hostOf(String value) {
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            java.net.URI uri = java.net.URI.create(value);
mport com.github.dropguard.summer.core.Internal;
            return uri.getHost();
mport com.github.dropguard.summer.core.Internal;
        } catch (IllegalArgumentException e) {
mport com.github.dropguard.summer.core.Internal;
            // A bare Host header ("localhost:8080") is not a valid URI; treat the whole value as
mport com.github.dropguard.summer.core.Internal;
            // host.
mport com.github.dropguard.summer.core.Internal;
            int colon = value.lastIndexOf(':');
mport com.github.dropguard.summer.core.Internal;
            return colon < 0 ? value : value.substring(0, colon);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static int portOf(String value, String host) {
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            java.net.URI uri = java.net.URI.create(value);
mport com.github.dropguard.summer.core.Internal;
            if (uri.getPort() != -1) {
mport com.github.dropguard.summer.core.Internal;
                return uri.getPort();
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            return "https".equals(uri.getScheme()) ? 443 : 80;
mport com.github.dropguard.summer.core.Internal;
        } catch (IllegalArgumentException e) {
mport com.github.dropguard.summer.core.Internal;
            int colon = value.lastIndexOf(':');
mport com.github.dropguard.summer.core.Internal;
            return colon < 0 ? 80 : Integer.parseInt(value.substring(colon + 1));
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
