package summer.web.health;

import summer.core.ApplicationState;
import summer.core.BeanContainer;
import summer.web.HttpRouter;
import summer.web.RouteRegistrar;

/**
 * Built-in health check routes. Exposes /health/ready which turns to 503 during
 * graceful shutdown.
 */
public class HealthRouteRegistrar implements RouteRegistrar {

	@Override
	public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
		builder.get("/health/ready", (ctx) -> {
			if (ApplicationState.isShuttingDown()) {
				ctx.json(summer.web.HttpStatus.SERVICE_UNAVAILABLE, java.util.Map.of("status", "SHUTTING_DOWN"));
			} else {
				ctx.json(summer.web.HttpStatus.OK, java.util.Map.of("status", "UP"));
			}
		});

		builder.get("/health/live", (ctx) -> {
			ctx.json(summer.web.HttpStatus.OK, java.util.Map.of("status", "UP"));
		});
	}
}
