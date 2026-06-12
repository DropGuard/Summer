package summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import summer.core.ApplicationContext;
import summer.tck.AbstractContextTCK;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.Request;

/**
 * TCK for web routing via the DI engine.
 *
 * <p>
 * Verifies that the DI engine correctly discovers controllers, registers routes
 * via the Pull Model ({@link summer.web.RouteProvider}), and produces a sealed
 * {@link HttpRouter} that dispatches requests to the right handlers.
 * </p>
 */
public abstract class AbstractWebRouteTCK extends AbstractContextTCK {

	protected HttpRouter router;
	protected ExceptionRegistry exceptionRegistry;

	@BeforeEach
	void setUpRouter() {
		// Force context initialization (not lazy)
		ApplicationContext ctx = context();

		summer.web.HttpRouter.Builder builder = new summer.web.HttpRouter.Builder(
				summer.web.http.RadixTreeHttpRouter::new);
		exceptionRegistry = new ExceptionRegistry();

		// Get registrars from context (they are @Component beans)
		for (summer.web.RouteRegistrar registrar : ctx.getBeans(summer.web.RouteRegistrar.class)) {
			registrar.registerControllers(builder, ctx);
		}
		for (summer.web.ExceptionHandlerRegistrar ehRegistrar : ctx
				.getBeans(summer.web.ExceptionHandlerRegistrar.class)) {
			ehRegistrar.registerHandlers(exceptionRegistry, ctx);
		}

		router = builder.build();
	}

	@ParameterizedTest
	@MethodSource("routeTestCases")
	void testRoute(HttpMethod method, String path, String body, String expected) {
		byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
		String contentType = body != null ? "application/json" : null;
		Request req = new Request(method, path, null, contentType, bodyBytes);
		HttpContext ctx = new HttpContext(req);

		router.route(ctx);
		assertEquals(expected, new String(ctx.body(), StandardCharsets.UTF_8));
	}

	static Stream<Arguments> routeTestCases() {
		return Stream.of(Arguments.of(HttpMethod.GET, "/api/users/456", null, "user:456"),
				Arguments.of(HttpMethod.POST, "/api/users", "{\"name\":\"Alice\"}", "created:Alice"),
				Arguments.of(HttpMethod.PUT, "/api/users/123", "{\"name\":\"Bob\"}", "updated:123:Bob"),
				Arguments.of(HttpMethod.DELETE, "/api/users/123", null, "deleted:123"),
				Arguments.of(HttpMethod.GET, "/api/users/secured", null, "secret"));
	}

	@Test
	void testExceptionHandlerIsTriggeredAndResolved() {
		Request req = new Request(HttpMethod.GET, "/api/users/error", null, null, null);
		HttpContext ctx = new HttpContext(req);

		try {
			router.route(ctx);
			fail("Expected router.route to propagate the exception from UserController");
		} catch (Exception e) {
			assertTrue(e instanceof IllegalArgumentException);

			// Resolve using the ExceptionRegistry
			Handler errHandler = exceptionRegistry.getHandler(e);
			assertNotNull(errHandler, "ExceptionHandler must be registered for IllegalArgumentException");

			ctx.request().setAttribute(summer.web.RequestAttributes.LAST_EXCEPTION, e);
			try {
				errHandler.handle(ctx);
				assertEquals("error_caught:invalid id", new String(ctx.body(), StandardCharsets.UTF_8));
			} catch (Exception ex) {
				fail("Exception handler handle() threw exception: " + ex.getMessage());
			}
		}
	}
}
