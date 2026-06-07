package summer.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.Request;

public class ByteRouterTest {

	private RadixTreeHttpRouter router;

	@BeforeEach
	void setUp() {
		router = new RadixTreeHttpRouter();
	}

	@Test
	void testStaticRouteMatching() {
		Handler handler = (ctx) -> "ok";
		router.register(HttpMethod.GET, "/api/users", handler);

		HttpContext ctx = createMockContext(HttpMethod.GET, "/api/users");

		Object result = router.route(ctx);
		assertNotNull(result);
		assertEquals("ok", result);
	}

	@Test
	void testPathParameterMatching() {
		Handler handler = (ctx) -> "user-" + ctx.request().pathParam("id");
		router.register(HttpMethod.GET, "/users/{id}", handler);

		HttpContext ctx = createMockContext(HttpMethod.GET, "/users/42");

		Object result = router.route(ctx);
		assertEquals("user-42", result);
	}

	@Test
	void testDeepNestedRoutes() {
		router.register(HttpMethod.GET, "/api/v1/products/search", (ctx) -> "search");
		router.register(HttpMethod.POST, "/api/v1/products", (ctx) -> "create");

		assertEquals("search", router.route(createMockContext(HttpMethod.GET, "/api/v1/products/search")));
		assertEquals("create", router.route(createMockContext(HttpMethod.POST, "/api/v1/products")));
		assertNull(router.route(createMockContext(HttpMethod.GET, "/api/v1/products")));
	}

	@Test
	void testRootPath() {
		router.register(HttpMethod.GET, "/", (ctx) -> "home");
		assertEquals("home", router.route(createMockContext(HttpMethod.GET, "/")));
		assertEquals("home", router.route(createMockContext(HttpMethod.GET, "")));
	}

	@Test
	void testTrailingSlash() {
		router.register(HttpMethod.GET, "/users", (ctx) -> "list");
		assertEquals("list", router.route(createMockContext(HttpMethod.GET, "/users/")));
	}

	private HttpContext createMockContext(HttpMethod method, String path) {
		byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
		Request request = new Request(method, path, "", "application/json", new byte[0], new HashMap<>(), pathBytes);
		return new HttpContext(request);
	}
}
