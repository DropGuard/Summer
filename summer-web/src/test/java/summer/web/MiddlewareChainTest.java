package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import summer.web.middleware.Middleware;

/**
 * Tests for middleware chain composition. Middleware in Summer wraps handlers
 * via Middleware.apply(next). The chain executes outermost → innermost (like an
 * onion).
 */
public class MiddlewareChainTest {

	private WebContext makeContext(String method, String path) {
		byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
		Request request = new Request(method, path, "", "application/json", new byte[0], new HashMap<>(), pathBytes);
		return new WebContext(request);
	}

	// ---- Reusable test middleware factory ----

	/**
	 * Creates a middleware that appends a label to a shared log before and after
	 * calling next.
	 */
	private Middleware tracingMiddleware(String label, List<String> log) {
		return next -> ctx -> {
			log.add("before:" + label);
			Object result = next.handle(ctx);
			log.add("after:" + label);
			return result;
		};
	}

	// ---- Tests ----

	@Test
	void testSingleMiddlewareWrapsHandler() {
		List<String> log = new ArrayList<>();

		Handler handler = ctx -> {
			log.add("handler");
			return "response";
		};

		Middleware mw = tracingMiddleware("A", log);
		Handler chain = mw.apply(handler);

		Object result = chain.handle(makeContext("GET", "/test"));

		assertEquals("response", result);
		assertEquals(List.of("before:A", "handler", "after:A"), log);
	}

	@Test
	void testMiddlewareChainOrderIsOuterToInner() {
		// When applying middleware in order A, B, C to a handler:
		// The last applied is the outermost wrapper.
		// So if we apply: handler → C → B → A, execution is A → B → C → handler → C → B
		// → A
		List<String> log = new ArrayList<>();

		Handler handler = ctx -> {
			log.add("handler");
			return "done";
		};

		// Apply in reverse: innermost first, outermost last
		Handler chain = handler;
		chain = tracingMiddleware("C", log).apply(chain);
		chain = tracingMiddleware("B", log).apply(chain);
		chain = tracingMiddleware("A", log).apply(chain);

		chain.handle(makeContext("GET", "/"));

		assertEquals(List.of("before:A", "before:B", "before:C", "handler", "after:C", "after:B", "after:A"), log);
	}

	@Test
	void testMiddlewareCanShortCircuit() {
		// A middleware that blocks the request and never calls next
		List<String> log = new ArrayList<>();

		Handler handler = ctx -> {
			log.add("handler"); // Should NOT be reached
			return "real-response";
		};

		Middleware authGuard = next -> ctx -> {
			log.add("auth-blocked");
			return "401 Unauthorized"; // Short-circuit — does NOT call next.handle(ctx)
		};

		Handler chain = authGuard.apply(handler);
		Object result = chain.handle(makeContext("GET", "/secret"));

		assertEquals("401 Unauthorized", result);
		assertFalse(log.contains("handler"), "Handler should not have been called");
		assertTrue(log.contains("auth-blocked"));
	}

	@Test
	void testMiddlewareCanModifyRequestAttribute() {
		// A middleware that enriches the request with a decoded "userId" attribute
		Middleware tokenDecoder = next -> ctx -> {
			// Simulate: decode "Bearer user-42" → set userId attribute
			String auth = ctx.request().getHeader("authorization");
			if (auth != null && auth.startsWith("Bearer ")) {
				ctx.request().setAttribute("userId", auth.substring("Bearer ".length()));
			}
			return next.handle(ctx);
		};

		Handler handler = ctx -> ctx.request().getAttribute("userId");

		Handler chain = tokenDecoder.apply(handler);

		// Build context with Authorization header
		HashMap<String, String> headers = new HashMap<>();
		headers.put("authorization", "Bearer user-42");
		byte[] pathBytes = "/profile".getBytes(StandardCharsets.UTF_8);
		Request request = new Request("GET", "/profile", "", "application/json", new byte[0], headers, pathBytes);
		WebContext ctx = new WebContext(request);

		Object result = chain.handle(ctx);
		assertEquals("user-42", result);
	}
}
