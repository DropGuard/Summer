package summer.web;

/**
 * Middleware interface for authentication. Implementations resolve the current
 * user's ID from the request and store it as a request attribute.
 *
 * <p>
 * Usage:
 * </p>
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
 * 	Long userId = ctx.request().getAttribute("userId", Long.class);
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
	 * @param ctx
	 *            the web context
	 * @return the user ID, or {@code null} if not authenticated
	 */
	Long authenticate(HttpContext ctx);

	/**
	 * Applies authentication: calls {@link #authenticate(HttpContext)} and stores
	 * the result as a request attribute named "userId".
	 *
	 * <p>
	 * If authentication fails (returns null), the attribute is not set. The handler
	 * is responsible for checking and returning 401 if needed.
	 * </p>
	 */
	@Override
	default Handler apply(Handler handler) {
		return ctx -> {
			Long userId = authenticate(ctx);
			if (userId != null) {
				ctx.request().setAttribute("userId", userId);
			}
			return handler.handle(ctx);
		};
	}
}
