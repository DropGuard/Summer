package com.github.dropguard.summer.tck.web.ws;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.tck.AbstractComponentTCK;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Abstract TCK for WebSocket routing behavior.
 *
 * <p>Tests that both WsRouter implementations (RadixWsRouter, MapWsRouter) handle WebSocket route
 * registration, matching, and path parameter extraction consistently.
 */
public abstract class AbstractWebSocketTCK extends AbstractComponentTCK {

    /** Subclasses return a builder pre-configured with the specific router implementation. */
    protected abstract WsRouter.Builder createBuilder();

    @Test
    void testWsRouteExactMatch() {
        AtomicReference<String> received = new AtomicReference<>();

        WsRouter router = createBuilder().ws("/chat", ctx -> received.set("connected")).build();

        WsRouter.WsMatch match = router.routeWs("/chat");
        assertNotNull(match, "Should match exact WebSocket route");
        assertTrue(
                match.pathParams() == null || match.pathParams().isEmpty(),
                "Exact match should have no path params");

        // Simulate handler invocation
        match.handler()
                .handle(
                        createMockContext(
                                match.pathParams() != null ? match.pathParams() : Map.of()));
        assertEquals("connected", received.get());
    }

    @Test
    void testWsRouteWithPathParam() {
        AtomicReference<String> roomRef = new AtomicReference<>();

        WsRouter router =
                createBuilder()
                        .ws("/chat/{room}", ctx -> roomRef.set(ctx.pathParam("room")))
                        .build();

        WsRouter.WsMatch match = router.routeWs("/chat/general");
        assertNotNull(match, "Should match WebSocket route with path param");
        assertEquals("general", match.pathParams().get("room"));

        // Simulate handler invocation
        match.handler().handle(createMockContext(match.pathParams()));
        assertEquals("general", roomRef.get());
    }

    @Test
    void testWsRouteWithMultiplePathParams() {
        AtomicReference<String> resultRef = new AtomicReference<>();

        WsRouter router =
                createBuilder()
                        .ws(
                                "/ws/{tenant}/{channel}",
                                ctx -> {
                                    String tenant = ctx.pathParam("tenant");
                                    String channel = ctx.pathParam("channel");
                                    resultRef.set(tenant + ":" + channel);
                                })
                        .build();

        WsRouter.WsMatch match = router.routeWs("/ws/acme/general");
        assertNotNull(match, "Should match WebSocket route with multiple params");
        assertEquals("acme", match.pathParams().get("tenant"));
        assertEquals("general", match.pathParams().get("channel"));

        // Simulate handler invocation
        match.handler().handle(createMockContext(match.pathParams()));
        assertEquals("acme:general", resultRef.get());
    }

    @Test
    void testWsRouteTrailingSlash() {
        AtomicReference<String> received = new AtomicReference<>();

        WsRouter router = createBuilder().ws("/chat", ctx -> received.set("connected")).build();

        WsRouter.WsMatch match = router.routeWs("/chat/");
        assertNotNull(match, "Should match WebSocket route with trailing slash");
    }

    @Test
    void testWsRouteNoMatch() {
        WsRouter router = createBuilder().ws("/chat", ctx -> {}).build();

        WsRouter.WsMatch match = router.routeWs("/other");
        assertNull(match, "Should not match unregistered WebSocket route");
    }

    @Test
    void testWsRouteMultipleHandlers() {
        AtomicReference<String> chatRef = new AtomicReference<>();
        AtomicReference<String> notifyRef = new AtomicReference<>();

        WsRouter router =
                createBuilder()
                        .ws("/chat", ctx -> chatRef.set("chat"))
                        .ws("/notify", ctx -> notifyRef.set("notify"))
                        .build();

        WsRouter.WsMatch chatMatch = router.routeWs("/chat");
        assertNotNull(chatMatch);
        chatMatch.handler().handle(createMockContext(Map.of()));
        assertEquals("chat", chatRef.get());

        WsRouter.WsMatch notifyMatch = router.routeWs("/notify");
        assertNotNull(notifyMatch);
        notifyMatch.handler().handle(createMockContext(Map.of()));
        assertEquals("notify", notifyRef.get());
    }

    @Test
    void testWsRouteWithWildcard() {
        AtomicReference<String> received = new AtomicReference<>();

        WsRouter router = createBuilder().ws("/ws/**", ctx -> received.set("wildcard")).build();

        WsRouter.WsMatch match = router.routeWs("/ws/any/path");
        assertNotNull(match, "Should match wildcard WebSocket route");

        // Simulate handler invocation
        match.handler().handle(createMockContext(Map.of()));
        assertEquals("wildcard", received.get());
    }

    /** Creates a mock WebSocketContext for testing. */
    private WebSocketContext createMockContext(Map<String, String> pathParams) {
        return new WebSocketContext() {
            @Override
            public String pathParam(String name) {
                return pathParams.get(name);
            }

            @Override
            public void close() {}

            @Override
            public String header(String name) {
                return null;
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

            @Override
            public <T> void onMessageAs(Class<T> type, Consumer<T> consumer) {
                // Not needed for this test
            }

            @Override
            public void sendJson(Object payload) {
                // Not needed for this test
            }
        };
    }
}
