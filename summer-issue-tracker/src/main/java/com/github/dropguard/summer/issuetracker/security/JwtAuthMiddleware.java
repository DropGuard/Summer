package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Order;
import com.github.dropguard.summer.web.AuthMiddleware;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;

/**
 * Demo auth middleware. Implements the framework's {@link AuthMiddleware} contract: {@link
 * #authenticate(HttpContext)} resolves the user id from the bearer token and returns it. The
 * framework's default {@code apply} then stores it as the {@code userId} request attribute — the
 * single channel handlers and middleware read it from. The chain composes {@code handler =
 * m.apply(handler)} in list order, so the LAST list entry runs FIRST — {@link RbacMiddleware}
 * carries {@code @Order(2)}, this {@code @Order(2)} puts auth before the gate.
 *
 * <p>Public routes (register/login/health) deliberately return {@code null} so no attribute is set
 * and downstream code treats the request as anonymous.
 */
@Component
@GlobalMiddleware
@Order(2)
public class JwtAuthMiddleware implements AuthMiddleware {

    private final JwtUtil jwtUtil;

    public JwtAuthMiddleware(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Long authenticate(HttpContext ctx) {
        if (PublicRoutes.isPublic(ctx)) {
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
}
