package summer.tck.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.validation.BodyValidator;
import summer.web.*;

public abstract class AbstractValidationTCK {

	static {
		System.setProperty("net.bytebuddy.experimental", "true");
	}

	protected ApplicationContext context;
	protected Router router;
	protected BodyValidator bodyValidator;

	protected abstract ApplicationContext createAndInitializeContext();

	@BeforeEach
	void setUp() {
		context = createAndInitializeContext();
		RouteRegistrar adapter = context.getBean(RouteRegistrar.class);
		adapter.registerControllers();
		router = context.getBean(Router.class);
		bodyValidator = context.getBean(BodyValidator.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
	}

	@Test
	void testValidationSuccess() {
		byte[] bodyBytes = "{\"name\":\"Alice\"}".getBytes(StandardCharsets.UTF_8);
		Request req = new Request("POST", "/api/validation/submit", null, "application/json", bodyBytes);
		WebContext ctx = new WebContext(req, bodyValidator, new JsonBodyConverter());

		Object result = router.route(ctx);
		assertEquals("ok:Alice", result);
	}

	@Test
	void testValidationFailureEmptyName() {
		byte[] bodyBytes = "{\"name\":\"\"}".getBytes(StandardCharsets.UTF_8);
		Request req = new Request("POST", "/api/validation/submit", null, "application/json", bodyBytes);
		WebContext ctx = new WebContext(req, bodyValidator, new JsonBodyConverter());

		RuntimeException ex = assertThrows(RuntimeException.class, () -> {
			router.route(ctx);
		});
		assertTrue(ex.getMessage().contains("Validation failed"),
				"Expected validation failure error message, got: " + ex.getMessage());
		assertTrue(
				ex.getMessage().contains("cannot be empty")
						|| ex.getMessage().contains("must be at least 2 characters"),
				"Expected specific validation error message, got: " + ex.getMessage());
	}

	@Test
	void testValidationFailureShortName() {
		byte[] bodyBytes = "{\"name\":\"A\"}".getBytes(StandardCharsets.UTF_8);
		Request req = new Request("POST", "/api/validation/submit", null, "application/json", bodyBytes);
		WebContext ctx = new WebContext(req, bodyValidator, new JsonBodyConverter());

		RuntimeException ex = assertThrows(RuntimeException.class, () -> {
			router.route(ctx);
		});
		assertTrue(ex.getMessage().contains("Validation failed"),
				"Expected validation failure error message, got: " + ex.getMessage());
		assertTrue(ex.getMessage().contains("must be at least 2 characters"),
				"Expected specific validation error message, got: " + ex.getMessage());
	}
}
