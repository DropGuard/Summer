package com.github.dropguard.summer.twitter.auth;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.common.BusinessException;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;

@Component
@GlobalMiddleware
public class AuthMiddleware implements Middleware {

    private final JwtUtil jwtUtil;

    public AuthMiddleware(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Handler apply(Handler next) {
        return (HttpContext ctx) -> {
            if (isPublicRequest(ctx)) {
                next.handle(ctx);
                return;
            }

            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new BusinessException(
                        HttpStatus.UNAUTHORIZED.code(),
                        "TOKEN_MISSING",
                        "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            Long userId = jwtUtil.validateAccessToken(token);
            ctx.request().setAttribute(RequestAttributes.USER_ID, userId);
            next.handle(ctx);
        };
    }

    /**
     * Routes that must be reachable without authentication: - Bootstrap auth (register, login) -
     * Health probes (liveness, readiness) - WebSocket upgrades (handlers authenticate via
     * subprotocol)
     */
    private static boolean isPublicRequest(HttpContext ctx) {
        String method = ctx.method().name();
        String path = ctx.path();

        // Bootstrap auth
        if ("POST".equals(method)
                && ("/api/auth/register".equals(path)
                        || "/api/auth/login".equals(path)
                        || "/api/auth/refresh".equals(path))) {
            return true;
        }

        // Health probes
        if ("GET".equals(method) && ("/health/live".equals(path) || "/health/ready".equals(path))) {
            return true;
        }

        // WebSocket upgrades — authenticated via Sec-WebSocket-Protocol in handlers
        if ("websocket".equalsIgnoreCase(ctx.header("Upgrade"))) {
            return true;
        }

        return false;
    }
}
