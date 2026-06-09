package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuthMiddleware}.
 */
class AuthMiddlewareTest {

	@Nested
	@DisplayName("Authentication success")
	class AuthenticationSuccessTests {

		@Test
		@DisplayName("authenticate returns userId -> attribute set, handler called")
		void authenticateSuccess() {
			// Given
			AtomicReference<Long> capturedUserId = new AtomicReference<>();
			AtomicBoolean handlerCalled = new AtomicBoolean(false);

			AuthMiddleware middleware = ctx -> 42L;

			Handler handler = ctx -> {
				capturedUserId.set(ctx.request().getAttribute("userId", Long.class));
				handlerCalled.set(true);
				return null;
			};

			// When
			Request request = createRequest(HttpMethod.GET, "/test");
			HttpContext ctx = new HttpContext(request);
			middleware.apply(handler).handle(ctx);

			// Then
			assertTrue(handlerCalled.get(), "Handler should be called");
			assertEquals(42L, capturedUserId.get(), "userId should be set");
		}

		@Test
		@DisplayName("authenticate returns 0 -> attribute set (edge case)")
		void authenticateZeroUserId() {
			// Given
			AtomicReference<Long> capturedUserId = new AtomicReference<>();

			AuthMiddleware middleware = ctx -> 0L;

			Handler handler = ctx -> {
				capturedUserId.set(ctx.request().getAttribute("userId", Long.class));
				return null;
			};

			// When
			Request request = createRequest(HttpMethod.GET, "/test");
			HttpContext ctx = new HttpContext(request);
			middleware.apply(handler).handle(ctx);

			// Then
			assertEquals(0L, capturedUserId.get(), "userId 0 should be valid");
		}
	}

	@Nested
	@DisplayName("Authentication failure")
	class AuthenticationFailureTests {

		@Test
		@DisplayName("authenticate returns null -> attribute not set, handler called")
		void authenticateFailure() {
			// Given
			AtomicReference<Long> capturedUserId = new AtomicReference<>();
			AtomicBoolean handlerCalled = new AtomicBoolean(false);

			AuthMiddleware middleware = ctx -> null;

			Handler handler = ctx -> {
				capturedUserId.set(ctx.request().getAttribute("userId", Long.class));
				handlerCalled.set(true);
				return null;
			};

			// When
			Request request = createRequest(HttpMethod.GET, "/test");
			HttpContext ctx = new HttpContext(request);
			middleware.apply(handler).handle(ctx);

			// Then
			assertTrue(handlerCalled.get(), "Handler should still be called");
			assertNull(capturedUserId.get(), "userId should not be set");
		}
	}

	@Nested
	@DisplayName("Handler chain ordering")
	class HandlerChainTests {

		@Test
		@DisplayName("multiple middlewares execute in order")
		void multipleMiddlewares() {
			// Given
			StringBuilder order = new StringBuilder();

			AuthMiddleware auth1 = ctx -> {
				order.append("auth1 ");
				ctx.request().setAttribute("userId", 1L);
				return 1L;
			};

			Middleware logging = handler -> ctx -> {
				order.append("logging ");
				return handler.handle(ctx);
			};

			Handler handler = ctx -> {
				order.append("handler");
				return null;
			};

			// When
			Request request = createRequest(HttpMethod.GET, "/test");
			HttpContext ctx = new HttpContext(request);

			// Apply: auth1 -> logging -> handler
			Handler chain = auth1.apply(logging.apply(handler));
			chain.handle(ctx);

			// Then
			assertEquals("auth1 logging handler", order.toString());
			assertEquals(1L, ctx.request().getAttribute("userId", Long.class));
		}
	}

	// Helper methods
	private Request createRequest(HttpMethod method, String path) {
		return new Request(method, path, null, "application/json", new byte[0]);
	}
}
