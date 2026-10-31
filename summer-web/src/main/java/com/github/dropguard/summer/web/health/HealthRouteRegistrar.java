package com.github.dropguard.summer.web.health;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.RouterAdapter;

/**
 * Built-in health check routes. Exposes /health/ready which turns to 503 during graceful shutdown.
 * The shutdown signal is the REGISTERED container's own ({@link BeanContainer#isShuttingDown()}),
 * never a process-wide flag — sequential test universes and embedded multi-container hosts must not
 * inherit a closed container's readiness.
 */
@Internal
public class HealthRouteRegistrar implements RouterAdapter {

    @Override
    public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
        builder.get(
                "/health/ready",
                (ctx) -> {
                    if (context.isShuttingDown()) {
                        ctx.json(
                                com.github.dropguard.summer.web.HttpStatus.SERVICE_UNAVAILABLE,
                                java.util.Map.of("status", "SHUTTING_DOWN"));
                    } else {
                        ctx.json(
                                com.github.dropguard.summer.web.HttpStatus.OK,
                                java.util.Map.of("status", "UP"));
                    }
                });

        builder.get(
                "/health/live",
                (ctx) -> {
                    ctx.json(
                            com.github.dropguard.summer.web.HttpStatus.OK,
                            java.util.Map.of("status", "UP"));
                });
    }
}
