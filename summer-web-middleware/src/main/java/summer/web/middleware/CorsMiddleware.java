package summer.web.middleware;

import summer.web.Handler;
import summer.web.HttpMethod;
import summer.web.HttpStatus;
import summer.web.Middleware;

/**
 * CORS middleware that adds Cross-Origin Resource Sharing headers to responses.
 *
 * <p>
 * This middleware handles CORS preflight (OPTIONS) requests and adds the
 * appropriate headers to all responses based on the {@link CorsConfig}
 * configuration.
 * </p>
 *
 * <p>
 * Example configuration in {@code application.yml}:
 * </p>
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
			// Set CORS headers
			ctx.setHeader("Access-Control-Allow-Origin", config.allowedOrigins());
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
}
