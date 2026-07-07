package summer.twitter.auth;

import io.jsonwebtoken.Claims;
import summer.core.Component;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.Middleware;
import summer.web.RequestAttributes;
import summer.web.HttpStatus;

@Component
public class AuthMiddleware implements Middleware {

    private final JwtUtil jwtUtil;

    public AuthMiddleware(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Handler apply(Handler next) {
        return (HttpContext ctx) -> {
            String authHeader = ctx.header("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ctx.text(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
                return;
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.extractClaims(token);
                Long userId = Long.valueOf(claims.getSubject());
                ctx.request().setAttribute(RequestAttributes.USER_ID, userId);
                next.handle(ctx);
            } catch (Exception e) {
                ctx.text(HttpStatus.UNAUTHORIZED, "Invalid token");
            }
        };
    }
}