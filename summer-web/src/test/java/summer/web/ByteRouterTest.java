package summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ByteRouterTest {

	private Router router;

	@BeforeEach
	void setUp() {
		router = new Router();
	}

	@Test
	void testStaticRouteMatching() {
		Handler handler = (ctx) -> "ok";
		router.register("GET", "/api/users", handler);

		// Mock context with byte path
		WebContext ctx = createMockContext("GET", "/api/users");

		Object result = router.route(ctx);
		assertNotNull(result);
		assertEquals("ok", result);
	}

	@Test
	void testPathParameterMatching() {
		Handler handler = (ctx) -> "user-" + ctx.request().pathParam("id");
		router.register("GET", "/users/{id}", handler);

		WebContext ctx = createMockContext("GET", "/users/42");

		Object result = router.route(ctx);
		assertEquals("user-42", result);
	}

	@Test
	void testDeepNestedRoutes() {
		router.register("GET", "/api/v1/products/search", (ctx) -> "search");
		router.register("POST", "/api/v1/products", (ctx) -> "create");

		assertEquals("search", router.route(createMockContext("GET", "/api/v1/products/search")));
		assertEquals("create", router.route(createMockContext("POST", "/api/v1/products")));
		assertNull(router.route(createMockContext("GET", "/api/v1/products"))); // 404
	}

	@Test
	void testRootPath() {
		router.register("GET", "/", (ctx) -> "home");
		assertEquals("home", router.route(createMockContext("GET", "/")));
		assertEquals("home", router.route(createMockContext("GET", "")));
	}

	@Test
	void testTrailingSlash() {
		router.register("GET", "/users", (ctx) -> "list");
		// Our current byte-router scans segments, so trailing slashes are naturally
		// ignored
		assertEquals("list", router.route(createMockContext("GET", "/users/")));
	}

	private WebContext createMockContext(String method, String path) {
		byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
		// Request constructor needs a query, contentType, body, and headers
		Request request = new Request(method, path, "", "application/json", new byte[0], new HashMap<>(), pathBytes);
		return new WebContext(request);
	}
}
