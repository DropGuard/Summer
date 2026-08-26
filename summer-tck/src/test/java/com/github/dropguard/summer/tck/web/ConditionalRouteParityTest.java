package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.Request;
import com.github.dropguard.summer.web.RouterAdapter;
import com.github.dropguard.summer.web.http.RadixTreeHttpRouter;

/**
 * Dual-engine parity: a controller whose {@code @ConditionalOnBean} is unsatisfied registers no
 * routes on EITHER engine.
 *
 * <p>Guards the route-scan timing contract (AOT audit 2026-08-10, A2): route collection runs after
 * condition evaluation on both engines, so a conditioned-out controller contributes nothing — the
 * runtime path scans post-condition already; the AOT path previously scanned pre-condition and
 * could register routes for beans that conditions then removed.
 */
@SummerTest
public class ConditionalRouteParityTest {

    @DualEngine
    void conditionedOutControllerRegistersNoRoutes(BeanContainer context) throws Exception {
        HttpRouter.Builder builder = new HttpRouter.Builder(RadixTreeHttpRouter::new);
        for (RouterAdapter registrar : context.getBeans(RouterAdapter.class)) {
            registrar.registerControllers(builder, context);
        }
        HttpRouter router = builder.build();

        // The hidden controller's condition is never satisfied → no route may exist.
        HttpContext hidden =
                new HttpContext(new Request(HttpMethod.GET, "/hidden", null, null, null));
        router.route(hidden);
        assertNull(hidden.status(), "conditioned-out controller must not be routable");

        // Negative control: an unconditional controller in the same universe still routes, so the
        // absence above is not a vacuous pass (no registrars / no routes at all).
        HttpContext visible =
                new HttpContext(new Request(HttpMethod.GET, "/api/users/1", null, null, null));
        router.route(visible);
        assertNotNull(visible.status(), "unconditional controller must still route");
    }
}
