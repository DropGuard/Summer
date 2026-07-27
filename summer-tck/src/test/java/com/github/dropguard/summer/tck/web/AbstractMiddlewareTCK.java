package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.tck.AbstractTCK;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.Request;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TCK for middleware behavior via the DI engine.
 *
 * <p>Verifies that the DI engine correctly wires middleware chains:
 *
 * <ul>
 *   <li>Route-level middleware via {@link com.github.dropguard.summer.web.RouteProvider} applies
 *       correctly
 *   <li>Multiple middlewares are applied in correct order
 *   <li>Global middlewares are applied to all routes
 *   <li>Global middlewares wrap route-level middlewares
 * </ul>
 *
 * <p>Routes with middleware are registered by {@code TestMiddlewareRouteProvider} --a
 * {@code @Component} that implements {@code RouteProvider}. The DI engine discovers it and invokes
 * {@code provide()} during router assembly, verifying the Pull Model end-to-end.
 *
 * <p>The container is supplied by the subclass constructor (the {@code @SummerTest} injection
 * contract) — this base class no longer builds its own context. The {@code @Test} methods hold the
 * assertions; a concrete {@code @SummerTest} subclass exposes them as {@code @DualEngine} methods
 * so the framework runs them on both DI engines (Runtime + AOT), proving engine parity.
 */
public abstract class AbstractMiddlewareTCK extends AbstractTCK {

    protected final BeanContainer context;
    protected HttpRouter router;
    protected List<Middleware> globalMiddlewares;

    protected AbstractMiddlewareTCK(BeanContainer context) {
        this.context = context;
    }

    @BeforeEach
    void setUpRouter() {
        BeanContainer ctx = context;

        com.github.dropguard.summer.web.HttpRouter.Builder builder =
                new com.github.dropguard.summer.web.HttpRouter.Builder(
                        com.github.dropguard.summer.web.http.RadixTreeHttpRouter::new);

        // Get registrars from context (they are @Component beans)
        for (com.github.dropguard.summer.web.RouteRegistrar registrar :
                ctx.getBeans(com.github.dropguard.summer.web.RouteRegistrar.class)) {
            registrar.registerControllers(builder, ctx);
        }

        com.github.dropguard.summer.fixtures.web.dummy.MyMiddleware myMiddleware =
                ctx.getBean(com.github.dropguard.summer.fixtures.web.dummy.MyMiddleware.class);
        com.github.dropguard.summer.fixtures.web.dummy.LoggingMiddleware loggingMiddleware =
                ctx.getBean(com.github.dropguard.summer.fixtures.web.dummy.LoggingMiddleware.class);

        builder.group(
                "/api/users",
                group -> {
                    group.use(myMiddleware);
                    group.get("/secured", c -> c.text(HttpStatus.OK, "secret"));
                    group.use(loggingMiddleware);
                    group.get("/multi", c -> c.text(HttpStatus.OK, "multi"));
                });

        builder.group(
                "/api/class-level",
                group -> {
                    group.use(loggingMiddleware);
                    group.get("/test", c -> c.text(HttpStatus.OK, "test"));
                });

        router = builder.build();

        // Global middlewares are collected the same way the web runner does: every
        // @GlobalMiddleware-annotated Middleware bean in the test universe, in
        // container registration order. This keeps the TCK faithful to the
        // production wiring contract (NettyServerRunner applies the same set)
        // without starting Netty here.
        globalMiddlewares = new java.util.ArrayList<>();
        for (com.github.dropguard.summer.web.Middleware m :
                ctx.getBeans(com.github.dropguard.summer.web.Middleware.class)) {
            if (m.getClass()
                    .isAnnotationPresent(
                            com.github.dropguard.summer.web.annotation.GlobalMiddleware.class)) {
                globalMiddlewares.add(m);
            }
        }
    }

    /** Route request through global middlewares and router. */
    private String routeWithMiddlewares(Request req) {
        HttpContext ctx = new HttpContext(req);

        // Build handler chain: global middlewares -> router
        com.github.dropguard.summer.web.Handler handler =
                c -> {
                    try {
                        router.route(c);
                    } catch (Exception e) {
                        c.error(e);
                    }
                };

        // Apply global middlewares
        for (Middleware middleware : globalMiddlewares) {
            handler = middleware.apply(handler);
        }

        handler.handle(ctx);
        byte[] body = ctx.body();
        return body != null ? new String(body, StandardCharsets.UTF_8) : null;
    }

    @Test
    void testMethodLevelMiddleware() {
        // Route-level middleware via RouteProvider
        Request req = new Request(HttpMethod.GET, "/api/users/secured", null, null, null);

        String result = routeWithMiddlewares(req);
        assertNotNull(result);
        assertEquals("[global-logged] [secured] secret", result);
    }

    @Test
    void testClassLevelMiddleware() {
        // Route group middleware via RouteProvider
        Request req = new Request(HttpMethod.GET, "/api/class-level/test", null, null, null);

        String result = routeWithMiddlewares(req);
        assertNotNull(result);
        assertEquals("[global-logged] [class-logged] test", result);
    }

    @Test
    void testMultipleMiddlewares() {
        // Multiple route-level middlewares via RouteProvider
        Request req = new Request(HttpMethod.GET, "/api/users/multi", null, null, null);

        String result = routeWithMiddlewares(req);
        assertNotNull(result);
        // MyMiddleware first, then LoggingMiddleware, wrapped by
        // GlobalLoggingMiddleware
        assertEquals("[global-logged] [secured] [class-logged] multi", result);
    }

    @Test
    void testGlobalMiddlewareAppliedToAllRoutes() {
        // GlobalLoggingMiddleware should wrap all routes
        Request req = new Request(HttpMethod.GET, "/api/users/123", null, null, null);

        String result = routeWithMiddlewares(req);
        assertNotNull(result);
        assertEquals("[global-logged] user:123", result);
    }
}
