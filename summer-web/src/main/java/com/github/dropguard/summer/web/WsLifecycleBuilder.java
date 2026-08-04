package com.github.dropguard.summer.web;

import com.github.dropguard.summer.web.websocket.WebSocketContext;
import java.util.function.Consumer;

/**
 * Builder for defining WebSocket lifecycle hooks on a specific path.
 *
 * <p>WebSocket routes are bound to a specific HTTP path (e.g., {@code /ws/chat}). Within that path,
 * users define lifecycle callbacks:
 *
 * <pre>{@code
 * router.bind("/ws/chat", ws -> {
 * 	ws.onConnect(ctx -> log.info("Connected")).onMessage(msg -> handleMessage(msg))
 * 			.onClose(() -> log.info("Closed"));
 * });
 * }</pre>
 */
interface WsLifecycleBuilder {

    /**
     * Registers a handler invoked when a WebSocket connection is established.
     *
     * @param handler the connect handler, receives the WebSocket context
     * @return this builder for chaining
     */
    WsLifecycleBuilder onConnect(Consumer<WebSocketContext> handler);

    /**
     * Registers a handler invoked when a text message is received.
     *
     * @param handler the message handler, receives the message text
     * @return this builder for chaining
     */
    WsLifecycleBuilder onMessage(Consumer<String> handler);

    /**
     * Registers a handler invoked when the WebSocket connection is closed.
     *
     * @param handler the close handler
     * @return this builder for chaining
     */
    WsLifecycleBuilder onClose(Runnable handler);
}
