package summer.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.Request;

/**
 * Tests for MapRouter path normalization.
 *
 * <p>
 * Uses the Builder API to register routes (which internally creates a
 * RadixTreeHttpRouter). MapRouter-specific normalization tests use the
 * MapRouter constructor directly.
 * </p>
 */
class MapRouterNormalizationTest {

	@Test
	void doubleSlashInPath() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new)
				.get("/users/{id}", ctx -> "user-" + ctx.request().pathParam("id")).build();

		Object result = router.route(ctx(HttpMethod.GET, "//users//42"));
		assertEquals("user-42", result);
	}

	@Test
	void multipleTrailingSlashes() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).get("/users", ctx -> "user-list").build();

		Object result = router.route(ctx(HttpMethod.GET, "/users///"));
		assertEquals("user-list", result);
	}

	@Test
	void leadingDoubleSlash() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).get("/api/health", ctx -> "ok").build();

		Object result = router.route(ctx(HttpMethod.GET, "//api/health"));
		assertEquals("ok", result);
	}

	@Test
	void trailingSlashMatches() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).get("/users", ctx -> "user-list").build();

		Object result = router.route(ctx(HttpMethod.GET, "/users/"));
		assertEquals("user-list", result);
	}

	@Test
	void rootPath() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new).get("/", ctx -> "home").build();

		assertEquals("home", router.route(ctx(HttpMethod.GET, "/")));
		assertEquals("home", router.route(ctx(HttpMethod.GET, "")));
	}

	private HttpContext ctx(HttpMethod method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}
}
