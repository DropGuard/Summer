package summer.tck.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.web.*;

public abstract class AbstractWebRouteTCK {

	protected ApplicationContext context;
	protected Router router;
	protected ExceptionRegistry exceptionRegistry;

	protected abstract ApplicationContext createAndInitializeContext();

	@BeforeEach
	void setUp() {
		context = createAndInitializeContext();
		RouteRegistrar adapter = context.getBean(RouteRegistrar.class);
		adapter.registerControllers();

		router = context.getBean(Router.class);
		exceptionRegistry = context.getBean(ExceptionRegistry.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	@Test
	void testGetRouteWithPathParam() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Request req = new Request("GET", "/api/users/456", null, null, null);
		Response res = new Response();
		WebContext ctx = new WebContext(req, res);

		Object result = router.route(ctx);
		assertEquals("user:456", result);
	}

	@Test
	void testPostRouteWithRequestBodyRecord() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] bodyBytes = "{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8);
		Request req = new Request("POST", "/api/users", null, "application/json", bodyBytes);
		Response res = new Response();
		WebContext ctx = new WebContext(req, res);

		Object result = router.route(ctx);
		assertEquals("created:Alice", result);
	}

	@Test
	void testPutRouteWithPathParamAndBody() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] bodyBytes = "{\"name\":\"Bob\"}".getBytes(StandardCharsets.UTF_8);
		Request req = new Request("PUT", "/api/users/123", null, "application/json", bodyBytes);
		Response res = new Response();
		WebContext ctx = new WebContext(req, res);

		Object result = router.route(ctx);
		assertEquals("updated:123:Bob", result);
	}

	@Test
	void testDeleteRouteWithPathParam() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Request req = new Request("DELETE", "/api/users/123", null, null, null);
		Response res = new Response();
		WebContext ctx = new WebContext(req, res);

		Object result = router.route(ctx);
		assertEquals("deleted:123", result);
	}

	@Test
	void testGetRouteWithMiddleware() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Request req = new Request("GET", "/api/users/secured", null, null, null);
		Response res = new Response();
		WebContext ctx = new WebContext(req, res);

		Object result = router.route(ctx);
		assertEquals("[secured] secret", result);
	}

	@Test
	void testExceptionHandlerIsTriggeredAndResolved() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Request req = new Request("GET", "/api/users/error", null, null, null);
		Response res = new Response();
		WebContext ctx = new WebContext(req, res);

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
