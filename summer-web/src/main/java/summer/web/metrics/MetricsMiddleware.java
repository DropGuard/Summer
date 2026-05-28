package summer.web.metrics;

import summer.web.Handler;
import summer.web.annotation.GlobalMiddleware;
import summer.web.middleware.Middleware;

/**
 * Middleware that tracks request counts, errors, and active connections. This
 * should be placed early in the middleware chain for accurate metrics.
 */
@GlobalMiddleware
public class MetricsMiddleware implements Middleware {

	private final MetricsRegistry registry;

	public MetricsMiddleware(MetricsRegistry registry) {
		this.registry = registry;
	}

	@Override
	public Handler apply(Handler next) {
		return (ctx) -> {
			registry.incrementActive();
			try {
				Object result = next.handle(ctx);

				// If status is an error status, record it
				if (ctx.response().getStatusCode() >= 500) {
					registry.recordError();
				}

				return result;
			} catch (Exception e) {
				registry.recordError();
				throw e;
			} finally {
				registry.decrementActive();
			}
		};
	}
}
