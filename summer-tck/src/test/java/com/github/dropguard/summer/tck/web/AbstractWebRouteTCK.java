package com.github.dropguard.summer.tck.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.tck.AbstractTCK;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.Request;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;

/**
 * TCK for web routing via the DI engine.
 *
 * <p>
 * Verifies that the DI engine correctly discovers controllers, registers routes
 * via the Pull Model ({@link com.github.dropguard.summer.web.RouteProvider}),
 * and produces a sealed {@link HttpRouter} that dispatches requests to the
 * right handlers.
 * </p>
 *
 * <p>
 * The container is supplied by the subclass constructor (the
 * {@code @SummerTest} injection contract) — this base class no longer builds
 * its own context. The {@code routeBehaviour()} /
 * {@code exceptionHandlerBehaviour()} templates hold the assertions; concrete
 * subclasses expose them either as plain {@code @Test} methods (Runtime) or as
 * {@code @DualEngine} methods (AOT parity).
 * </p>
 */
public abstract class AbstractWebRouteTCK extends AbstractTCK {

	protected final BeanContainer context;
	protected HttpRouter router;
	protected ExceptionRegistry exceptionRegistry;

	protected AbstractWebRouteTCK(BeanContainer context) {
		this.context = context;
	}

	@BeforeEach
	void setUpRouter() {
		com.github.dropguard.summer.web.HttpRouter.Builder builder = new com.github.dropguard.summer.web.HttpRouter.Builder(
				com.github.dropguard.summer.web.http.RadixTreeHttpRouter::new);
		exceptionRegistry = new ExceptionRegistry();

		// Get registrars from context (they are @Component beans)
		for (com.github.dropguard.summer.web.RouteRegistrar registrar : context
				.getBeans(com.github.dropguard.summer.web.RouteRegistrar.class)) {
			registrar.registerControllers(builder, context);
		}
		for (com.github.dropguard.summer.web.ExceptionHandlerRegistrar ehRegistrar : context
				.getBeans(com.github.dropguard.summer.web.ExceptionHandlerRegistrar.class)) {
			ehRegistrar.registerHandlers(exceptionRegistry, context);
		}

		router = builder.build();
	}

	/**
	 * Routing assertions, parameterised by HTTP method/path/body/expected body.
	 * Subclasses expose this through {@code @ParameterizedTest} (Runtime) or
	 * {@code @DualEngine} (AOT parity).
	 */
	protected void routeBehaviour(HttpMethod method, String path, String body, String expected) {
		byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
		String contentType = body != null ? "application/json" : null;
		Request req = new Request(method, path, null, contentType, bodyBytes);
		HttpContext ctx = new HttpContext(req);

		router.route(ctx);
		assertEquals(expected, new String(ctx.body(), StandardCharsets.UTF_8));
	}

	/**
	 * Runs {@link #routeBehaviour(HttpMethod, String, String, String)} for every
	 * case from {@link #routeTestCases()}. Used by the dual-engine path so a single
	 * {@code @DualEngine} method exercises all routing cases on both engines (the
	 * two repetition axes — engine and case — cannot share one method, so the case
	 * axis is folded into the body here).
	 */
	protected void routeBehaviour() {
		routeTestCases().forEach(args -> {
			Object[] v = args.get();
			routeBehaviour((HttpMethod) v[0], (String) v[1], (String) v[2], (String) v[3]);
		});
	}

	/**
	 * Mechanism-level routing contract. Paths are business-agnostic on purpose and
	 * match {@code RoutingFixtureController} (the TCK fixture that backs this TCK).
	 * Keep the two in sync: each case here must have a corresponding handler there.
	 */
	static Stream<Arguments> routeTestCases() {
		return Stream.of(Arguments.of(HttpMethod.GET, "/rt/users/456", null, "user:456"),
				Arguments.of(HttpMethod.POST, "/rt/users", "{\"name\":\"Alice\"}", "created:Alice"),
				Arguments.of(HttpMethod.PUT, "/rt/users/123", "{\"name\":\"Bob\"}", "updated:123:Bob"),
				Arguments.of(HttpMethod.DELETE, "/rt/users/123", null, "deleted:123"),
				Arguments.of(HttpMethod.GET, "/rt/secured", null, "secret"));
	}

	/**
	 * Exception-handler triggering and resolution assertions. Subclasses expose
	 * this through {@code @Test} (Runtime) or {@code @DualEngine} (AOT parity).
	 */
	protected void exceptionHandlerBehaviour() {
		Request req = new Request(HttpMethod.GET, "/rt/error", null, null, null);
		HttpContext ctx = new HttpContext(req);

		try {
			router.route(ctx);
			fail("Expected router.route to propagate the exception from UserController");
		} catch (Exception e) {
			assertTrue(e instanceof IllegalArgumentException);

			// Resolve using the ExceptionRegistry
			Handler errHandler = exceptionRegistry.getHandler(e);
			assertNotNull(errHandler, "ExceptionHandler must be registered for IllegalArgumentException");

			ctx.request().setAttribute(com.github.dropguard.summer.web.RequestAttributes.LAST_EXCEPTION, e);
			try {
				errHandler.handle(ctx);
				assertEquals("error_caught:invalid id", new String(ctx.body(), StandardCharsets.UTF_8));
			} catch (Exception ex) {
				fail("Exception handler handle() threw exception: " + ex.getMessage());
			}
		}
	}
}
