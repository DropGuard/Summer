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
 */
public class CorsMiddleware implements Middleware {

    private final CorsConfig config;

    public CorsMiddleware(CorsConfig config) {
        this.config = config;
    }

    @Override
    public Handler apply(Handler next) {
        return ctx -> {
            // Reflect the matched origin per the CORS spec: "*" stays "*"; a named allow-list
            // reflects the requesting origin (never the raw configured list). Matching is delegated
            // to OriginPolicy so it stays consistent with the WebSocket upgrade guard.
            List<String> allowed = parseOrigins(config.allowedOrigins());
            String origin = ctx.header("Origin");
            String host = ctx.header("Host");
            if (allowed.contains("*")) {
                ctx.setHeader("Access-Control-Allow-Origin", "*");
            } else if (origin != null && OriginPolicy.isAllowed(origin, allowed, host)) {
                ctx.setHeader("Access-Control-Allow-Origin", origin);
            }

            ctx.setHeader("Access-Control-Allow-Methods", config.allowedMethods());
            ctx.setHeader("Access-Control-Allow-Headers", config.allowedHeaders());
            ctx.setHeader("Access-Control-Max-Age", String.valueOf(config.maxAge()));

            // Handle preflight OPTIONS request
            if (HttpMethod.OPTIONS == ctx.method()) {
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
