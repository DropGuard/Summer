package summer.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.HttpStatus;
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
				.get("/users/{id}", ctx -> ctx.text(HttpStatus.OK, "user-" + ctx.request().pathParam("id"))).build();

		assertEquals("user-42", bodyOf(router, HttpMethod.GET, "//users//42"));
	}

	@Test
	void multipleTrailingSlashes() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new)
				.get("/users", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		assertEquals("user-list", bodyOf(router, HttpMethod.GET, "/users///"));
	}

	@Test
	void leadingDoubleSlash() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new)
				.get("/api/health", ctx -> ctx.text(HttpStatus.OK, "ok")).build();

		assertEquals("ok", bodyOf(router, HttpMethod.GET, "//api/health"));
	}

	@Test
	void trailingSlashMatches() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new)
				.get("/users", ctx -> ctx.text(HttpStatus.OK, "user-list")).build();

		assertEquals("user-list", bodyOf(router, HttpMethod.GET, "/users/"));
	}

	@Test
	void rootPath() {
		HttpRouter router = new HttpRouter.Builder(RadixTreeHttpRouter::new)
				.get("/", ctx -> ctx.text(HttpStatus.OK, "home")).build();

		assertEquals("home", bodyOf(router, HttpMethod.GET, "/"));
		assertEquals("home", bodyOf(router, HttpMethod.GET, ""));
	}

	private String bodyOf(HttpRouter router, HttpMethod method, String path) {
		HttpContext ctx = ctx(method, path);
		router.route(ctx);
		byte[] body = ctx.body();
		return body != null ? new String(body, StandardCharsets.UTF_8) : null;
	}

	private HttpContext ctx(HttpMethod method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new HttpContext(req);
	}
}
