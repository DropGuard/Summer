package summer.tck.web.ws;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import summer.web.WsRouter;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WebSocketHandler;

/**
 * Abstract TCK for WebSocket routing behavior.
 *
 * <p>Tests that both WsRouter implementations (RadixWsRouter, MapRouter) handle
 * WebSocket route registration, matching, and path parameter extraction
 * consistently.</p>
 */
public abstract class AbstractWebSocketTCK {

	protected abstract WsRouter createRouter();

	@Test
	void testWsRouteExactMatch() {
		WsRouter router = createRouter();
		AtomicReference<String> received = new AtomicReference<>();

		router.ws("/chat", ctx -> {
			received.set("connected");
		});

		WsRouter.WsMatch match = router.routeWs("/chat");
		assertNotNull(match, "Should match exact WebSocket route");
		assertTrue(match.pathParams == null || match.pathParams.isEmpty(),
				"Exact match should have no path params");

		// Simulate handler invocation
		match.handler.handle(createMockContext(match.pathParams != null ? match.pathParams : Map.of()));
		assertEquals("connected", received.get());
	}

	@Test
	void testWsRouteWithPathParam() {
		WsRouter router = createRouter();
		AtomicReference<String> roomRef = new AtomicReference<>();

		router.ws("/chat/{room}", ctx -> {
			roomRef.set(ctx.pathParam("room"));
		});

		WsRouter.WsMatch match = router.routeWs("/chat/general");
		assertNotNull(match, "Should match WebSocket route with path param");
		assertEquals("general", match.pathParams.get("room"));

		// Simulate handler invocation
		match.handler.handle(createMockContext(match.pathParams));
		assertEquals("general", roomRef.get());
	}

	@Test
	void testWsRouteWithMultiplePathParams() {
		WsRouter router = createRouter();
		AtomicReference<String> resultRef = new AtomicReference<>();

		router.ws("/ws/{tenant}/{channel}", ctx -> {
			String tenant = ctx.pathParam("tenant");
			String channel = ctx.pathParam("channel");
			resultRef.set(tenant + ":" + channel);
		});

		WsRouter.WsMatch match = router.routeWs("/ws/acme/general");
		assertNotNull(match, "Should match WebSocket route with multiple params");
		assertEquals("acme", match.pathParams.get("tenant"));
		assertEquals("general", match.pathParams.get("channel"));

		// Simulate handler invocation
		match.handler.handle(createMockContext(match.pathParams));
		assertEquals("acme:general", resultRef.get());
	}

	@Test
	void testWsRouteTrailingSlash() {
		WsRouter router = createRouter();
		AtomicReference<String> received = new AtomicReference<>();

		router.ws("/chat", ctx -> {
			received.set("connected");
		});

		WsRouter.WsMatch match = router.routeWs("/chat/");
		assertNotNull(match, "Should match WebSocket route with trailing slash");
	}

	@Test
	void testWsRouteNoMatch() {
		WsRouter router = createRouter();

		router.ws("/chat", ctx -> {});

		WsRouter.WsMatch match = router.routeWs("/other");
		assertNull(match, "Should not match unregistered WebSocket route");
	}

	@Test
	void testWsRouteMultipleHandlers() {
		WsRouter router = createRouter();
		AtomicReference<String> chatRef = new AtomicReference<>();
		AtomicReference<String> notifyRef = new AtomicReference<>();

		router.ws("/chat", ctx -> chatRef.set("chat"));
		router.ws("/notify", ctx -> notifyRef.set("notify"));

		WsRouter.WsMatch chatMatch = router.routeWs("/chat");
		assertNotNull(chatMatch);
		chatMatch.handler.handle(createMockContext(Map.of()));
		assertEquals("chat", chatRef.get());

		WsRouter.WsMatch notifyMatch = router.routeWs("/notify");
		assertNotNull(notifyMatch);
		notifyMatch.handler.handle(createMockContext(Map.of()));
		assertEquals("notify", notifyRef.get());
	}

	@Test
	void testWsRouteWithWildcard() {
		WsRouter router = createRouter();
		AtomicReference<String> received = new AtomicReference<>();

		router.ws("/ws/**", ctx -> {
			received.set("wildcard");
		});

		WsRouter.WsMatch match = router.routeWs("/ws/any/path");
		assertNotNull(match, "Should match wildcard WebSocket route");

		// Simulate handler invocation
		match.handler.handle(createMockContext(Map.of()));
		assertEquals("wildcard", received.get());
	}

	/**
	 * Creates a mock WebSocketContext for testing.
	 */
	private WebSocketContext createMockContext(Map<String, String> pathParams) {
		return new WebSocketContext() {
			@Override
			public String pathParam(String name) {
				return pathParams.get(name);
			}

			@Override
			public void send(String text) {
				// Mock: no-op
			}

			@Override
			public void onMessage(Consumer<String> consumer) {
				// Mock: no-op
			}

			@Override
			public void onClose(Runnable onClose) {
				// Mock: no-op
			}
		};
	}
}
