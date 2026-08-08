package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.AuthMiddleware;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;

/**
 * Demo auth middleware. Implements the framework's {@link AuthMiddleware} contract: {@link
 * #authenticate(HttpContext)} resolves the user id from the bearer token and returns it. The
 * framework's default {@code apply} then stores it as the {@code userId} request attribute AND
 * publishes a {@link com.github.dropguard.summer.web.RequestContext} via {@link
 * com.github.dropguard.summer.web.RequestContextHolder}, so service beans and AOP interceptors can
 * read the current user without it being threaded through method signatures.
 *
 * <p>Public routes (register/login/health) deliberately return {@code null} so the holder stays
 * unbound and downstream code treats the request as anonymous.
 */
@Component
@GlobalMiddleware
public class JwtAuthMiddleware implements AuthMiddleware {

    private final JwtUtil jwtUtil;

    public JwtAuthMiddleware(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Long authenticate(HttpContext ctx) {
        if (isPublicRequest(ctx)) {
            return null;
        }
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtUtil.validateAccessToken(authHeader.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPublicRequest(HttpContext ctx) {
        String method = ctx.method().name();
        String path = ctx.path();
        if ("POST".equals(method)
                && ("/api/auth/register".equals(path)
                        || "/api/auth/login".equals(path)
                        || "/api/auth/refresh".equals(path))) {
            return true;
        }
        return "GET".equals(method)
                && ("/health/live".equals(path) || "/health/ready".equals(path));
    }
}
