package summer.tck.web.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.Request;
import summer.tck.AbstractComponentTCK;

/**
 * Technology Compatibility Kit for HttpRouter implementations.
 *
 * <p>
 * Ensures all HttpRouter implementations (RadixTreeHttpRouter, MapRouter, etc.)
 * behave identically for common routing scenarios. Subclasses provide the
 * router factory, and each test builds its own router via the Builder API.
 * </p>
 */
public abstract class AbstractRouterTCK extends AbstractComponentTCK {

	/**
	 * Subclasses return the router factory to test.
	 */
	protected abstract java.util.function.Function<java.util.List<HttpRouter.Builder.Route>, HttpRouter> routerFactory();

	// --- Static route matching ---

	@Test
	void testExactStaticRoute() {
		HttpRouter r = builder().get("/users", ctx -> "user-list").build();

		Object result = r.route(ctx(HttpMethod.GET, "/users"));
		assertEquals("user-list", result);
	}

	@Test
	void testMultiSegmentStaticRoute() {
		HttpRouter r = builder().get("/api/v1/users", ctx -> "v1-users").build();

		Object result = r.route(ctx(HttpMethod.GET, "/api/v1/users"));
		assertEquals("v1-users", result);
	}

	@Test
	void testUnmatchedRouteReturnsNull() {
		HttpRouter r = builder().get("/users", ctx -> "user-list").build();

		Object result = r.route(ctx(HttpMethod.GET, "/posts"));
		assertNull(result);
	}

	// --- Path parameters ---

	@Test
	void testSinglePathParam() {
		HttpRouter r = builder()
				.get("/users/{id}", ctx -> "user-" + ctx.request().pathParam("id"))
				.build();

		Object result = r.route(ctx(HttpMethod.GET, "/users/42"));
		assertEquals("user-42", result);
	}

	@Test
	void testMultiplePathParams() {
		HttpRouter r = builder()
				.get("/users/{userId}/posts/{postId}",
						ctx -> ctx.request().pathParam("userId") + ":" + ctx.request().pathParam("postId"))
				.build();

		Object result = r.route(ctx(HttpMethod.GET, "/users/10/posts/20"));
		assertEquals("10:20", result);
	}

	@Test
	void testPathParamAtEnd() {
		HttpRouter r = builder()
				.get("/files/{name}", ctx -> "file-" + ctx.request().pathParam("name"))
				.build();

		Object result = r.route(ctx(HttpMethod.GET, "/files/report.pdf"));
		assertEquals("file-report.pdf", result);
	}

	@Test
	void testPathParamAtStart() {
		HttpRouter r = builder()
				.get("{tenant}/users", ctx -> "tenant-" + ctx.request().pathParam("tenant"))
				.build();

		Object result = r.route(ctx(HttpMethod.GET, "acme/users"));
		assertEquals("tenant-acme", result);
	}

	// --- HTTP method isolation ---

	@Test
	void testSamePathDifferentMethods() {
		HttpRouter r = builder()
				.get("/users", ctx -> "get-users")
				.post("/users", ctx -> "post-users")
				.put("/users", ctx -> "put-users")
				.delete("/users", ctx -> "delete-users")
				.build();

		assertEquals("get-users", r.route(ctx(HttpMethod.GET, "/users")));
		assertEquals("post-users", r.route(ctx(HttpMethod.POST, "/users")));
		assertEquals("put-users", r.route(ctx(HttpMethod.PUT, "/users")));
		assertEquals("delete-users", r.route(ctx(HttpMethod.DELETE, "/users")));
	}

	@Test
	void testWrongMethodReturnsNull() {
		HttpRouter r = builder()
				.get("/users", ctx -> "get-users")
				.build();

		Object result = r.route(ctx(HttpMethod.POST, "/users"));
		assertNull(result);
	}

	// --- Path normalization ---

	@Test
	void testTrailingSlashMatches() {
		HttpRouter r = builder().get("/users", ctx -> "user-list").build();

		Object result = r.route(ctx(HttpMethod.GET, "/users/"));
		assertEquals("user-list", result);
	}

	@Test
	void testTrailingSlashOnRegistration() {
		HttpRouter r = builder().get("/users/", ctx -> "user-list").build();

		Object result = r.route(ctx(HttpMethod.GET, "/users"));
		assertEquals("user-list", result);
	}

	@Test
	void testRootPath() {
		HttpRouter r = builder().get("/", ctx -> "home").build();

		assertEquals("home", r.route(ctx(HttpMethod.GET, "/")));
		assertEquals("home", r.route(ctx(HttpMethod.GET, "")));
	}

	@Test
	void testDoubleSlashNormalization() {
		HttpRouter r = builder()
				.get("/users/{id}", ctx -> "user-" + ctx.request().pathParam("id"))
				.build();

		Object result = r.route(ctx(HttpMethod.GET, "//users//42"));
		assertEquals("user-42", result);
	}

	@Test
	void testMultipleTrailingSlashes() {
		HttpRouter r = builder().get("/users", ctx -> "user-list").build();

		Object result = r.route(ctx(HttpMethod.GET, "/users///"));
		assertEquals("user-list", result);
	}

	@Test
	void testLeadingDoubleSlash() {
		HttpRouter r = builder().get("/api/health", ctx -> "ok").build();

		Object result = r.route(ctx(HttpMethod.GET, "//api/health"));
		assertEquals("ok", result);
	}

	// --- Edge cases ---

	@Test
	void testEmptyPathSegments() {
		HttpRouter r = builder().get("/api///users", ctx -> "users").build();

		Object result = r.route(ctx(HttpMethod.GET, "/api/users"));
		assertEquals("users", result);
	}

	@Test
	void testPathParamWithSpecialChars() {
		HttpRouter r = builder()
				.get("/files/{name}", ctx -> ctx.request().pathParam("name"))
				.build();

		Object result = r.route(ctx(HttpMethod.GET, "/files/my%20file.txt"));
		assertNotNull(result);
		assertEquals("my file.txt", result);
	}

	// --- Wildcard matching ---

	@Test
	void testSingleSegmentWildcard() {
		HttpRouter r = builder()
				.get("/files/*", ctx -> "file-wildcard")
				.build();

		assertEquals("file-wildcard", r.route(ctx(HttpMethod.GET, "/files/a.txt")));
		assertEquals("file-wildcard", r.route(ctx(HttpMethod.GET, "/files/report.pdf")));
		assertNull(r.route(ctx(HttpMethod.GET, "/files/sub/dir")));
	}

	@Test
	void testMultiSegmentWildcard() {
		HttpRouter r = builder()
				.get("/api/**", ctx -> "catch-all")
				.build();

		assertEquals("catch-all", r.route(ctx(HttpMethod.GET, "/api/users")));
		assertEquals("catch-all", r.route(ctx(HttpMethod.GET, "/api/users/123")));
		assertEquals("catch-all", r.route(ctx(HttpMethod.GET, "/api/v1/posts/456")));
	}

	@Test
	void testWildcardWithStaticPrefix() {
		HttpRouter r = builder()
				.get("/static/**", ctx -> "static-files")
				.build();

		assertEquals("static-files", r.route(ctx(HttpMethod.GET, "/static/css/main.css")));
		assertEquals("static-files", r.route(ctx(HttpMethod.GET, "/static/js/app.js")));
		assertNull(r.route(ctx(HttpMethod.GET, "/api/users")));
	}

	// --- Helper ---

	private HttpRouter.Builder builder() {
		return new HttpRouter.Builder(routerFactory());
	}

	private HttpContext ctx(HttpMethod method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}
}
