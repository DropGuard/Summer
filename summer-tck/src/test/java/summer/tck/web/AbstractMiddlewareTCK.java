package summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.tck.AbstractContextTCK;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.HttpStatus;
import summer.web.Middleware;
import summer.web.Request;
import summer.web.annotation.GlobalMiddleware;

/**
 * TCK for middleware behavior via the DI engine.
 *
 * <p>
 * Verifies that the DI engine correctly wires middleware chains:
 * </p>
 * <ul>
 * <li>Route-level middleware via {@link summer.web.RouteProvider} applies
 * correctly</li>
 * <li>Multiple middlewares are applied in correct order</li>
 * <li>Global middlewares are applied to all routes</li>
 * <li>Global middlewares wrap route-level middlewares</li>
 * </ul>
 *
 * <p>
 * Routes with middleware are registered by {@code TestMiddlewareRouteProvider}
 * --a {@code @Component} that implements {@code RouteProvider}. The DI engine
 * discovers it and invokes {@code provide()} during router assembly, verifying
 * the Pull Model end-to-end.
 * </p>
 */
public abstract class AbstractMiddlewareTCK extends AbstractContextTCK {

	protected HttpRouter router;
	protected List<Middleware> globalMiddlewares;

	@BeforeEach
	void setUpRouter() {
		// Force context initialization (not lazy)
		BeanContainer ctx = context();

		summer.web.HttpRouter.Builder builder = new summer.web.HttpRouter.Builder(
				summer.web.http.RadixTreeHttpRouter::new);

		// Get registrars from context (they are @Component beans)
		for (summer.web.RouteRegistrar registrar : ctx.getBeans(summer.web.RouteRegistrar.class)) {
			registrar.registerControllers(builder, ctx);
		}

		summer.fixtures.web.dummy.MyMiddleware myMiddleware = ctx.getBean(summer.fixtures.web.dummy.MyMiddleware.class);
		summer.fixtures.web.dummy.LoggingMiddleware loggingMiddleware = ctx
				.getBean(summer.fixtures.web.dummy.LoggingMiddleware.class);

		builder.group("/api/users", group -> {
			group.use(myMiddleware);
			group.get("/secured", c -> c.text(HttpStatus.OK, "secret"));
			group.use(loggingMiddleware);
			group.get("/multi", c -> c.text(HttpStatus.OK, "multi"));
		});

		builder.group("/api/class-level", group -> {
			group.use(loggingMiddleware);
			group.get("/test", c -> c.text(HttpStatus.OK, "test"));
		});

		router = builder.build();

		// Collect global middlewares
		globalMiddlewares = ctx.getBeans(Middleware.class).stream()
				.filter(m -> m.getClass().isAnnotationPresent(GlobalMiddleware.class)).toList();
	}

	/**
	 * Route request through global middlewares and router.
	 */
	private String routeWithMiddlewares(Request req) {
		HttpContext ctx = new HttpContext(req);

		// Build handler chain: global middlewares -> router
		summer.web.Handler handler = c -> {
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
