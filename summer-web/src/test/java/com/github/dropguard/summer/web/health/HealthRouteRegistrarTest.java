package com.github.dropguard.summer.web.health;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.HttpStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The readiness route answers per-container: UP while this container is running, 503 once THIS
 * container's close() started, and UP again for a container built afterwards — the process-wide
 * shutdown flag the route used to read poisoned every later container in the same JVM.
 */
class HealthRouteRegistrarTest {

    private static Handler readyHandlerOf(BeanContainer container) {
        List<HttpRouter.Builder.Route> routes = new ArrayList<>();
        HttpRouter.Builder builder =
                new HttpRouter.Builder(
                        resolved -> {
                            routes.addAll(resolved);
                            return null;
                        });
        new HealthRouteRegistrar().registerControllers(builder, container);
        builder.build();
        return routes.stream()
                .filter(r -> r.path().equals("/health/ready"))
                .findFirst()
                .map(HttpRouter.Builder.Route::handler)
                .orElseThrow(() -> new AssertionError("registrar must expose /health/ready"));
    }

    @Test
    void readinessFollowsItsOwnContainerAcrossSequentialContainers() throws Exception {
        BeanContainer.Builder first = new BeanContainer.Builder();
        first.register(String.class, "first");
        BeanContainer closing = first.build();

        HttpContext fresh = mock(HttpContext.class);
        Handler handler = readyHandlerOf(closing);
        assertNotNull(handler);
        handler.handle(fresh);
        verify(fresh).json(eq(HttpStatus.OK), any());

        closing.close();

        HttpContext shuttingDown = mock(HttpContext.class);
        handler.handle(shuttingDown);
        verify(shuttingDown).json(eq(HttpStatus.SERVICE_UNAVAILABLE), any());

        BeanContainer.Builder second = new BeanContainer.Builder();
        second.register(String.class, "second");
        BeanContainer reborn = second.build();

        HttpContext afterRebuild = mock(HttpContext.class);
        readyHandlerOf(reborn).handle(afterRebuild);
        verify(afterRebuild).json(eq(HttpStatus.OK), any());
    }
}
