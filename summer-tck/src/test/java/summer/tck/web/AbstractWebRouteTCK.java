package summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import summer.core.ApplicationContext;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpRouter;
import summer.web.Request;
import summer.web.RouteRegistrar;

public abstract class AbstractWebRouteTCK {

	protected ApplicationContext context;
	protected HttpRouter router;
	protected ExceptionRegistry exceptionRegistry;

	protected abstract ApplicationContext createAndInitializeContext();

	@BeforeEach
	void setUp() {
		context = createAndInitializeContext();
		RouteRegistrar adapter = context.getBean(RouteRegistrar.class);
		adapter.registerControllers();

		router = context.getBean(HttpRouter.class);
		exceptionRegistry = context.getBean(ExceptionRegistry.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	@ParameterizedTest
	@MethodSource("routeTestCases")
	void testRoute(String method, String path, String body, String expected) {
		byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
		String contentType = body != null ? "application/json" : null;
		Request req = new Request(method, path, null, contentType, bodyBytes);
		HttpContext ctx = new HttpContext(req);

		Object result = router.route(ctx);
		assertEquals(expected, result);
	}

	static Stream<Arguments> routeTestCases() {
		return Stream.of(
				Arguments.of("GET", "/api/users/456", null, "user:456"),
				Arguments.of("POST", "/api/users", "{\"name\":\"Alice\"}", "created:Alice"),
				Arguments.of("PUT", "/api/users/123", "{\"name\":\"Bob\"}", "updated:123:Bob"),
				Arguments.of("DELETE", "/api/users/123", null, "deleted:123"),
				Arguments.of("GET", "/api/users/secured", null, "[secured] secret"));
	}

	@Test
	void testExceptionHandlerIsTriggeredAndResolved() {
		Request req = new Request("GET", "/api/users/error", null, null, null);
		HttpContext ctx = new HttpContext(req);

		try {
			router.route(ctx);
			fail("Expected router.route to propagate the exception from UserController");
		} catch (Exception e) {
			assertTrue(e instanceof IllegalArgumentException);

			// Resolve using the ExceptionRegistry
			Handler errHandler = exceptionRegistry.getHandler(e);
			assertNotNull(errHandler, "ExceptionHandler must be registered for IllegalArgumentException");

			ctx.request().setAttribute("last_exception", e);
			try {
				Object errResult = errHandler.handle(ctx);
				assertEquals("error_caught:invalid id", errResult);
			} catch (Exception ex) {
				fail("Exception handler handle() threw exception: " + ex.getMessage());
			}
		}
	}
}
