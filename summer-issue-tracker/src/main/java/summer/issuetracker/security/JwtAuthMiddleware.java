package summer.issuetracker.security;

import io.jsonwebtoken.Claims;

import summer.core.Component;
import summer.web.AuthMiddleware;
import summer.web.HttpContext;
import summer.web.annotation.GlobalMiddleware;

/**
 * Demo auth middleware. Implements the framework's {@link AuthMiddleware}
 * contract: {@link #authenticate(HttpContext)} resolves the user id from the
 * bearer token and returns it. The framework's default {@code apply} then stores
 * it as the {@code userId} request attribute AND publishes a {@link
 * summer.web.RequestContext} via {@link summer.web.RequestContextHolder}, so
 * service beans and AOP interceptors can read the current user without it being
 * threaded through method signatures.
 *
 * <p>
 * Public routes (register/login/health) deliberately return {@code null} so the
 * holder stays unbound and downstream code treats the request as anonymous.
 * </p>
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
            Claims claims = jwtUtil.extractClaims(authHeader.substring(7));
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isPublicRequest(HttpContext ctx) {
        String method = ctx.method().name();
        String path = ctx.path();
        if ("POST".equals(method) && ("/api/auth/register".equals(path) || "/api/auth/login".equals(path))) {
            return true;
        }
        return "GET".equals(method) && ("/health/live".equals(path) || "/health/ready".equals(path));
    }
}
