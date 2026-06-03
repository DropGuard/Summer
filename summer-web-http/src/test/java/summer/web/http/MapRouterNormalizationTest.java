package summer.web.http;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpRouter;
import summer.web.Request;

/**
 * Tests for MapRouter path normalization.
 */
class MapRouterNormalizationTest {

	private HttpRouter router;

	@BeforeEach
	void setUp() {
		router = new MapRouter();
	}

	@Test
	void doubleSlashInPath() {
		router.register("GET", "/users/{id}", ctx -> "user-" + ctx.request().pathParam("id"));

		Object result = router.route(ctx("GET", "//users//42"));
		assertEquals("user-42", result);
	}

	@Test
	void multipleTrailingSlashes() {
		router.register("GET", "/users", ctx -> "user-list");

		Object result = router.route(ctx("GET", "/users///"));
		assertEquals("user-list", result);
	}

	@Test
	void leadingDoubleSlash() {
		router.register("GET", "/api/health", ctx -> "ok");

		Object result = router.route(ctx("GET", "//api/health"));
		assertEquals("ok", result);
	}

	@Test
	void trailingSlashMatches() {
		router.register("GET", "/users", ctx -> "user-list");

		Object result = router.route(ctx("GET", "/users/"));
		assertEquals("user-list", result);
	}

	@Test
	void rootPath() {
		router.register("GET", "/", ctx -> "home");

		assertEquals("home", router.route(ctx("GET", "/")));
		assertEquals("home", router.route(ctx("GET", "")));
	}

	private HttpContext ctx(String method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}
}
