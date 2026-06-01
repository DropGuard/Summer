package summer.tck.web.router;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.web.*;
import summer.web.websocket.WebSocketHandler;

/**
 * Technology Compatibility Kit for Router implementations.
 *
 * <p>
 * Ensures all Router implementations (RadixRouter, MapRouter, etc.) behave
 * identically for common routing scenarios. Subclasses provide the concrete
 * Router instance.
 * </p>
 */
public abstract class AbstractRouterTCK {

	protected Router router;

	/**
	 * Subclasses return the Router implementation to test.
	 */
	protected abstract Router createRouter();

	@BeforeEach
	void setUp() {
		router = createRouter();
	}

	// --- Static route matching ---

	@Test
	void testExactStaticRoute() {
		router.register("GET", "/users", ctx -> "user-list");

		Object result = router.route(ctx("GET", "/users"));
		assertEquals("user-list", result);
	}

	@Test
	void testMultiSegmentStaticRoute() {
		router.register("GET", "/api/v1/users", ctx -> "v1-users");

		Object result = router.route(ctx("GET", "/api/v1/users"));
		assertEquals("v1-users", result);
	}

	@Test
	void testUnmatchedRouteReturnsNull() {
		router.register("GET", "/users", ctx -> "user-list");

		Object result = router.route(ctx("GET", "/posts"));
		assertNull(result);
	}

	// --- Path parameters ---

	@Test
	void testSinglePathParam() {
		router.register("GET", "/users/{id}", ctx -> "user-" + ctx.request().pathParam("id"));

		Object result = router.route(ctx("GET", "/users/42"));
		assertEquals("user-42", result);
	}

	@Test
	void testMultiplePathParams() {
		router.register("GET", "/users/{userId}/posts/{postId}",
				ctx -> ctx.request().pathParam("userId") + ":" + ctx.request().pathParam("postId"));

		Object result = router.route(ctx("GET", "/users/10/posts/20"));
		assertEquals("10:20", result);
	}

	@Test
	void testPathParamAtEnd() {
		router.register("GET", "/files/{name}", ctx -> "file-" + ctx.request().pathParam("name"));

		Object result = router.route(ctx("GET", "/files/report.pdf"));
		assertEquals("file-report.pdf", result);
	}

	@Test
	void testPathParamAtStart() {
		router.register("GET", "{tenant}/users", ctx -> "tenant-" + ctx.request().pathParam("tenant"));

		Object result = router.route(ctx("GET", "acme/users"));
		assertEquals("tenant-acme", result);
	}

	// --- HTTP method isolation ---

	@Test
	void testSamePathDifferentMethods() {
		router.register("GET", "/users", ctx -> "get-users");
		router.register("POST", "/users", ctx -> "post-users");
		router.register("PUT", "/users", ctx -> "put-users");
		router.register("DELETE", "/users", ctx -> "delete-users");

		assertEquals("get-users", router.route(ctx("GET", "/users")));
		assertEquals("post-users", router.route(ctx("POST", "/users")));
		assertEquals("put-users", router.route(ctx("PUT", "/users")));
		assertEquals("delete-users", router.route(ctx("DELETE", "/users")));
	}

	@Test
	void testWrongMethodReturnsNull() {
		router.register("GET", "/users", ctx -> "get-users");

		Object result = router.route(ctx("POST", "/users"));
		assertNull(result);
	}

	// --- Path normalization ---

	@Test
	void testTrailingSlashMatches() {
		router.register("GET", "/users", ctx -> "user-list");

		// /users/ should match /users
		Object result = router.route(ctx("GET", "/users/"));
		assertEquals("user-list", result);
	}

	@Test
	void testTrailingSlashOnRegistration() {
		router.register("GET", "/users/", ctx -> "user-list");

		// /users should match /users/
		Object result = router.route(ctx("GET", "/users"));
		assertEquals("user-list", result);
	}

	@Test
	void testRootPath() {
		router.register("GET", "/", ctx -> "home");

		assertEquals("home", router.route(ctx("GET", "/")));
		assertEquals("home", router.route(ctx("GET", "")));
	}

	// --- Default method helpers ---

	@Test
	void testGetHelper() {
		router.get("/items", ctx -> "items");

		assertEquals("items", router.route(ctx("GET", "/items")));
	}

	@Test
	void testPostHelper() {
		router.post("/items", ctx -> "create");

		assertEquals("create", router.route(ctx("POST", "/items")));
	}

	@Test
	void testPutHelper() {
		router.put("/items/{id}", ctx -> "update");

		assertEquals("update", router.route(ctx("PUT", "/items/1")));
	}

	@Test
	void testDeleteHelper() {
		router.delete("/items/{id}", ctx -> "delete");

		assertEquals("delete", router.route(ctx("DELETE", "/items/1")));
	}

	// --- Edge cases ---

	@Test
	void testEmptyPathSegments() {
		router.register("GET", "/api///users", ctx -> "users");

		Object result = router.route(ctx("GET", "/api/users"));
		assertEquals("users", result);
	}

	@Test
	void testPathParamWithSpecialChars() {
		router.register("GET", "/files/{name}", ctx -> ctx.request().pathParam("name"));

		Object result = router.route(ctx("GET", "/files/my%20file.txt"));
		assertNotNull(result);
		// URL decoding should be applied
		assertEquals("my file.txt", result);
	}

	// --- Wildcard matching ---

	@Test
	void testSingleSegmentWildcard() {
		router.register("GET", "/files/*", ctx -> "file-wildcard");

		// Should match any single segment
		assertEquals("file-wildcard", router.route(ctx("GET", "/files/a.txt")));
		assertEquals("file-wildcard", router.route(ctx("GET", "/files/report.pdf")));
		// Should not match multiple segments
		assertNull(router.route(ctx("GET", "/files/sub/dir")));
	}

	@Test
	void testMultiSegmentWildcard() {
		router.register("GET", "/api/**", ctx -> "catch-all");

		// Should match any path under /api/
		assertEquals("catch-all", router.route(ctx("GET", "/api/users")));
		assertEquals("catch-all", router.route(ctx("GET", "/api/users/123")));
		assertEquals("catch-all", router.route(ctx("GET", "/api/v1/posts/456")));
	}

	@Test
	void testWildcardWithStaticPrefix() {
		router.register("GET", "/static/**", ctx -> "static-files");

		assertEquals("static-files", router.route(ctx("GET", "/static/css/main.css")));
		assertEquals("static-files", router.route(ctx("GET", "/static/js/app.js")));
		assertNull(router.route(ctx("GET", "/api/users")));
	}

	// --- WebSocket routing ---

	@Test
	void testWsRouteExact() {
		WebSocketHandler wsHandler = ctx -> {
		};
		router.ws("/chat", wsHandler);

		Router.WsMatch match = router.routeWs("/chat");
		assertNotNull(match);
		assertSame(wsHandler, match.handler);
		assertTrue(match.pathParams.isEmpty());
	}

	@Test
	void testWsRouteWithParams() {
		WebSocketHandler wsHandler = ctx -> {
		};
		router.ws("/ws/{channel}", wsHandler);

		Router.WsMatch match = router.routeWs("/ws/general");
		assertNotNull(match);
		assertSame(wsHandler, match.handler);
		assertEquals("general", match.pathParams.get("channel"));
	}

	@Test
	void testWsRouteTrailingSlash() {
		WebSocketHandler wsHandler = ctx -> {
		};
		router.ws("/chat", wsHandler);

		Router.WsMatch match = router.routeWs("/chat/");
		assertNotNull(match);
		assertSame(wsHandler, match.handler);
	}

	@Test
	void testWsRouteNoMatch() {
		WebSocketHandler wsHandler = ctx -> {
		};
		router.ws("/chat", wsHandler);

		Router.WsMatch match = router.routeWs("/other");
		assertNull(match);
	}

	// --- Helper ---

	private WebContext ctx(String method, String path) {
		Request req = new Request(method, path, null, null, new byte[0]);
		return new WebContext(req);
	}
}
