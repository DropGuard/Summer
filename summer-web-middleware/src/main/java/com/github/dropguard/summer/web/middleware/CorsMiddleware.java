package com.github.dropguard.summer.web.middleware;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.OriginPolicy;
import java.util.Arrays;
import java.util.List;

/**
 * CORS middleware that adds Cross-Origin Resource Sharing headers to responses.
 *
 * <p>This middleware handles CORS preflight (OPTIONS) requests and adds the appropriate headers to
 * all responses based on the {@link CorsConfig} configuration.
 *
 * <p>Example configuration in {@code application.yml}:
 *
 * <pre>{@code
 * cors:
 *   allowed-origins: "*"
 *   allowed-methods: "GET, POST, PUT, DELETE, OPTIONS"
 *   allowed-headers: "Content-Type, Authorization"
 *   max-age: 3600
 * }</pre>
 *
 * <p><strong>Contract.</strong> Requests without an {@code Origin} header pass through untouched.
 * Disallowed origins get {@code 403} with no CORS headers, for preflight and actual requests alike.
 * Policy headers (Allow-Methods / Allow-Headers / Max-Age) appear on preflight responses only;
 * every Origin-bearing response carries {@code Vary: Origin}.
 *
 * <p>This middleware does NOT support credentialed cross-origin requests: there is no
 * allow-credentials switch and none is planned implicitly — combining one with {@code
 * allowed-origins: "*"} would violate the fetch specification. If you need cookie-authenticated
 * cross-origin calls today, list explicit origins in {@code allowed-origins} and attach the
 * credentials on your framework's response path yourself. This middleware answers the browser's
 * CORS policy questions only; it is distinct from {@code server.allowed-origins}, which governs
 * WebSocket upgrade origins and is always active.
 */
public class CorsMiddleware implements Middleware {

    private final CorsConfig config;

    public CorsMiddleware(CorsConfig config) {
        this.config = config;
    }

    @Override
    public Handler apply(Handler next) {
        return ctx -> {
            String origin = ctx.header("Origin");
            if (origin == null) {
                // Not a CORS request — zero interference, not even Vary.
                next.handle(ctx);
                return;
            }
            // Origin-dependent response: caches must key on the request's Origin header, or a
            // cache shared across origins can serve one origin's CORS verdict to another.
            ctx.setHeader("Vary", "Origin");

            List<String> allowed = parseOrigins(config.allowedOrigins());
            if (!OriginPolicy.isAllowed(origin, allowed, ctx.header("Host"))) {
                // Same loud denial for preflight and actual requests: 403, no CORS headers at
                // all. Browsers would block anyway via missing Allow-Origin; the explicit status
                // makes server-side logs and curl probes tell the truth.
                ctx.status(HttpStatus.FORBIDDEN);
                return;
            }

            // Reflect per spec: "*" stays literal "*"; a named allow-list reflects the requesting
            // origin (never the raw configured list). Matching itself lives in OriginPolicy so it
            // stays consistent with the WebSocket upgrade guard.
            ctx.setHeader("Access-Control-Allow-Origin", allowed.contains("*") ? "*" : origin);

            // Preflight per spec: OPTIONS carrying Access-Control-Request-Method. Policy headers
            // belong here only — emitting them on actual responses is noise. Plain OPTIONS (curl
            // probes, WebDAV-style routes) passes through to routing untouched.
            boolean preflight =
                    HttpMethod.OPTIONS == ctx.method()
                            && ctx.header("Access-Control-Request-Method") != null;
            if (preflight) {
                ctx.setHeader("Access-Control-Allow-Methods", config.allowedMethods());
                ctx.setHeader("Access-Control-Allow-Headers", config.allowedHeaders());
                ctx.setHeader("Access-Control-Max-Age", String.valueOf(config.maxAge()));
                ctx.status(HttpStatus.NO_CONTENT);
                return;
            }

            next.handle(ctx);
        };
    }

    private static List<String> parseOrigins(String allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
