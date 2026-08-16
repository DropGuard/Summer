package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.web.HttpContext;

/**
 * The demo's public (unauthenticated) routes. Shared by the auth and RBAC middleware so the "no
 * user here" set is defined in exactly one place.
 */
public final class PublicRoutes {

    private PublicRoutes() {}

    public static boolean isPublic(HttpContext ctx) {
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
