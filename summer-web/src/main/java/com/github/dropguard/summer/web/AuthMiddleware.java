package com.github.dropguard.summer.web;

/**
 * Middleware interface for authentication. Implementations resolve the current user's ID from the
 * request and store it as a request attribute.
 *
 * <p>Usage:
 *
 * <pre>
 * {
 * 	&#64;code
 * 	// 1. Implement
 * 	&#64;Component
 * 	public class JwtAuthMiddleware implements AuthMiddleware {
 * 		@Override
 * 		public Long authenticate(HttpContext ctx) {
 * 			String token = ctx.request().getHeader("Authorization");
 * 			// validate token, return userId or null
 * 			return userId;
 * 		}
 * 	}
 *
 * 	// 2. Use in route group
 * 	router.group("/api", api -> {
 * 		api.use(new JwtAuthMiddleware());
 * 		api.get("/users", UserController::list);
 * 	});
 *
 * 	// 3. In handler, check userId
 * 	Long userId = ctx.request().getAttribute(RequestAttributes.USER_ID);
 * 	if (userId == null) {
 * 		ctx.json(401, Errors.tokenMissing());
 * 		return;
 * 	}
 * }
 * </pre>
 */
public interface AuthMiddleware extends Middleware {

    /**
     * Resolves the current user's ID from the request.
     *
     * @param ctx the web context
     * @return the user ID, or {@code null} if not authenticated
     */
    Long authenticate(HttpContext ctx);

    /**
     * Applies authentication: calls {@link #authenticate(HttpContext)} and stores the result as a
     * request attribute named "userId".
     *
     * <p>On success it also publishes the authenticated context via {@code RequestContextHolder}
     * (static {@code ThreadLocal}) so services and AOP interceptors can read the current user
     * without threading it through parameters. This side effect is intentional — it is how the
     * framework exposes the authenticated user outside the handler signature.
     *
     * <p>If authentication fails (returns null), neither the attribute nor the holder is set. The
     * handler is responsible for checking and returning 401 if needed.
     */
    @Override
    default Handler apply(Handler handler) {
        return ctx -> {
            Long userId = authenticate(ctx);
            if (userId != null) {
                ctx.request().setAttribute(RequestAttributes.USER_ID, userId);
                // Publish the authenticated context so services and AOP interceptors
                // can read the current user without it being threaded as a parameter.
                RequestContextHolder.set(new RequestContext(ctx.request(), userId));
            }
            handler.handle(ctx);
        };
    }
}
