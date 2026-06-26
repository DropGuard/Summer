package summer.tck.web.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import summer.tck.AbstractComponentTCK;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.HttpStatus;
import summer.web.Request;

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
		HttpRouter r = builder().get("/users", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		r.route(ctx(HttpMethod.GET, "/users"));
		assertEquals("user-list", bodyAsString(r, HttpMethod.GET, "/users"));
	}

	@Test
	void testMultiSegmentStaticRoute() {
		HttpRouter r = builder().get("/api/v1/users", ctx -> ctx.text(HttpStatus.OK, "v1-users")).build();

		assertEquals("v1-users", bodyAsString(r, HttpMethod.GET, "/api/v1/users"));
	}

	@Test
	void testUnmatchedRouteReturnsNull() {
		HttpRouter r = builder().get("/users", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		HttpContext ctx = ctx(HttpMethod.GET, "/posts");
		r.route(ctx);
		assertNull(ctx.body());
	}

	// --- Path parameters ---

	@Test
	void testSinglePathParam() {
		HttpRouter r = builder()
				.get("/users/{id}", ctx -> ctx.text(HttpStatus.OK, "user-" + ctx.request().pathParam("id"))).build();

		assertEquals("user-42", bodyAsString(r, HttpMethod.GET, "/users/42"));
	}

	@Test
	void testMultiplePathParams() {
		HttpRouter r = builder().get("/users/{userId}/posts/{postId}", ctx -> ctx.text(HttpStatus.OK,
				ctx.request().pathParam("userId") + ":" + ctx.request().pathParam("postId"))).build();

		assertEquals("10:20", bodyAsString(r, HttpMethod.GET, "/users/10/posts/20"));
	}

	@Test
	void testPathParamAtEnd() {
		HttpRouter r = builder()
				.get("/files/{name}", ctx -> ctx.text(HttpStatus.OK, "file-" + ctx.request().pathParam("name")))
				.build();

		assertEquals("file-report.pdf", bodyAsString(r, HttpMethod.GET, "/files/report.pdf"));
	}

	@Test
	void testPathParamAtStart() {
		HttpRouter r = builder()
				.get("{tenant}/users", ctx -> ctx.text(HttpStatus.OK, "tenant-" + ctx.request().pathParam("tenant")))
				.build();

		assertEquals("tenant-acme", bodyAsString(r, HttpMethod.GET, "acme/users"));
	}

	// --- HTTP method isolation ---

	@Test
	void testSamePathDifferentMethods() {
		HttpRouter r = builder().get("/users", ctx -> ctx.text(HttpStatus.OK, "get-users"))
				.post("/users", ctx -> ctx.text(HttpStatus.OK, "post-users"))
				.put("/users", ctx -> ctx.text(HttpStatus.OK, "put-users"))
				.delete("/users", ctx -> ctx.text(HttpStatus.OK, "delete-users")).build();

		assertEquals("get-users", bodyAsString(r, HttpMethod.GET, "/users"));
		assertEquals("post-users", bodyAsString(r, HttpMethod.POST, "/users"));
		assertEquals("put-users", bodyAsString(r, HttpMethod.PUT, "/users"));
		assertEquals("delete-users", bodyAsString(r, HttpMethod.DELETE, "/users"));
	}

	@Test
	void testWrongMethodReturnsNull() {
		HttpRouter r = builder().get("/users", ctx -> ctx.text(HttpStatus.OK, "get-users")).build();

		HttpContext ctx = ctx(HttpMethod.POST, "/users");
		r.route(ctx);
		assertNull(ctx.body());
	}

	// --- Path normalization ---

	@Test
	void testTrailingSlashMatches() {
		HttpRouter r = builder().get("/users", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		assertEquals("user-list", bodyAsString(r, HttpMethod.GET, "/users/"));
	}

	@Test
	void testTrailingSlashOnRegistration() {
		HttpRouter r = builder().get("/users/", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		assertEquals("user-list", bodyAsString(r, HttpMethod.GET, "/users"));
	}

	@Test
	void testRootPath() {
		HttpRouter r = builder().get("/", ctx -> ctx.text(HttpStatus.OK, "home")).build();

		assertEquals("home", bodyAsString(r, HttpMethod.GET, "/"));
		assertEquals("home", bodyAsString(r, HttpMethod.GET, ""));
	}

	@Test
	void testDoubleSlashNormalization() {
		HttpRouter r = builder()
				.get("/users/{id}", ctx -> ctx.text(HttpStatus.OK, "user-" + ctx.request().pathParam("id"))).build();

		assertEquals("user-42", bodyAsString(r, HttpMethod.GET, "//users//42"));
	}

	@Test
	void testMultipleTrailingSlashes() {
		HttpRouter r = builder().get("/users", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		assertEquals("user-list", bodyAsString(r, HttpMethod.GET, "/users///"));
	}

	@Test
	void testLeadingDoubleSlash() {
		HttpRouter r = builder().get("/api/health", ctx -> ctx.text(HttpStatus.OK, "ok")).build();

		assertEquals("ok", bodyAsString(r, HttpMethod.GET, "//api/health"));
	}

	// --- Edge cases ---

	@Test
	void testEmptyPathSegments() {
		HttpRouter r = builder().get("/api///users", ctx -> ctx.text(HttpStatus.OK, "users")).build();

		assertEquals("users", bodyAsString(r, HttpMethod.GET, "/api/users"));
	}

	@Test
	void testPathParamWithSpecialChars() {
		HttpRouter r = builder().get("/files/{name}", ctx -> ctx.text(HttpStatus.OK, ctx.request().pathParam("name")))
				.build();

		String result = bodyAsString(r, HttpMethod.GET, "/files/my%20file.txt");
		assertNotNull(result);
		assertEquals("my file.txt", result);
	}

	// --- Wildcard matching ---

	@Test
	void testSingleSegmentWildcard() {
		HttpRouter r = builder().get("/files/*", ctx -> ctx.text(HttpStatus.OK, "file-wildcard")).build();

		assertEquals("file-wildcard", bodyAsString(r, HttpMethod.GET, "/files/a.txt"));
		assertEquals("file-wildcard", bodyAsString(r, HttpMethod.GET, "/files/report.pdf"));

		HttpContext noMatch = ctx(HttpMethod.GET, "/files/sub/dir");
		r.route(noMatch);
		assertNull(noMatch.body());
	}

	@Test
	void testMultiSegmentWildcard() {
		HttpRouter r = builder().get("/api/**", ctx -> ctx.text(HttpStatus.OK, "catch-all")).build();

		assertEquals("catch-all", bodyAsString(r, HttpMethod.GET, "/api/users"));
		assertEquals("catch-all", bodyAsString(r, HttpMethod.GET, "/api/users/123"));
		assertEquals("catch-all", bodyAsString(r, HttpMethod.GET, "/api/v1/posts/456"));
	}

	@Test
	void testWildcardWithStaticPrefix() {
		HttpRouter r = builder().get("/static/**", ctx -> ctx.text(HttpStatus.OK, "static-files")).build();

		assertEquals("static-files", bodyAsString(r, HttpMethod.GET, "/static/css/main.css"));
		assertEquals("static-files", bodyAsString(r, HttpMethod.GET, "/static/js/app.js"));

		HttpContext noMatch = ctx(HttpMethod.GET, "/api/users");
		r.route(noMatch);
		assertNull(noMatch.body());
	}

	// --- Helper ---

	private HttpRouter.Builder builder() {
		return new HttpRouter.Builder(routerFactory());
	}

	private HttpContext ctx(HttpMethod method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}

	private String bodyAsString(HttpRouter r, HttpMethod method, String path) {
		HttpContext ctx = ctx(method, path);
		r.route(ctx);
		byte[] body = ctx.body();
		return body != null ? new String(body, StandardCharsets.UTF_8) : null;
	}
}
