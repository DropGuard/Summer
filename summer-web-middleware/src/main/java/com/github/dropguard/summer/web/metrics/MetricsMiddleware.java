package com.github.dropguard.summer.web.metrics;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Middleware;

/**
 * Middleware that tracks request counts, errors, and active connections. This should be placed
 * early in the middleware chain for accurate metrics.
 */
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
                next.handle(ctx);

                // If status is an error status, record it
                HttpStatus status = ctx.statusCode();
                if (status != null && status.code() >= 500) {
                    registry.recordError();
                }
            } catch (Exception e) {
                registry.recordError();
                throw e;
            } finally {
                registry.decrementActive();
            }
        };
    }
}
