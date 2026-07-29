mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web.health;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.ApplicationState;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.BeanContainer;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.HttpRouter;
@Internal
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.RouteRegistrar;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Built-in health check routes. Exposes /health/ready which turns to 503 during graceful shutdown.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class HealthRouteRegistrar implements RouteRegistrar {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
mport com.github.dropguard.summer.core.Internal;
        builder.get(
mport com.github.dropguard.summer.core.Internal;
                "/health/ready",
mport com.github.dropguard.summer.core.Internal;
                (ctx) -> {
mport com.github.dropguard.summer.core.Internal;
                    if (ApplicationState.isShuttingDown()) {
mport com.github.dropguard.summer.core.Internal;
                        ctx.json(
mport com.github.dropguard.summer.core.Internal;
                                com.github.dropguard.summer.web.HttpStatus.SERVICE_UNAVAILABLE,
mport com.github.dropguard.summer.core.Internal;
                                java.util.Map.of("status", "SHUTTING_DOWN"));
mport com.github.dropguard.summer.core.Internal;
                    } else {
mport com.github.dropguard.summer.core.Internal;
                        ctx.json(
mport com.github.dropguard.summer.core.Internal;
                                com.github.dropguard.summer.web.HttpStatus.OK,
mport com.github.dropguard.summer.core.Internal;
                                java.util.Map.of("status", "UP"));
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                });
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        builder.get(
mport com.github.dropguard.summer.core.Internal;
                "/health/live",
mport com.github.dropguard.summer.core.Internal;
                (ctx) -> {
mport com.github.dropguard.summer.core.Internal;
                    ctx.json(
mport com.github.dropguard.summer.core.Internal;
                            com.github.dropguard.summer.web.HttpStatus.OK,
mport com.github.dropguard.summer.core.Internal;
                            java.util.Map.of("status", "UP"));
mport com.github.dropguard.summer.core.Internal;
                });
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
