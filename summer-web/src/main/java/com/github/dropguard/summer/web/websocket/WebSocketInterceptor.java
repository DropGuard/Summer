package com.github.dropguard.summer.web.websocket;

/**
 * Interceptor for WebSocket text messages. Allows executing logic before a text message is
 * processed by the handler: either call {@link WebSocketInterceptorChain#proceed} to pass the
 * message to the next interceptor (or the final handler), or short-circuit the chain entirely (drop
 * the message).
 *
 * <p>Interceptors are route-scoped only: registered explicitly via {@code
 * WsRouter.Builder.intercept(path, ...)} for exactly one route, in registration order — observe,
 * filter and {@code proceed} with a rewritten message (per-route codecs, protocol framing). There
 * is no global collection: a bean with no registration site must not touch every connection of an
 * application. Registration lives in the route declaration, where a reader can see it.
 *
 * <p>The WebSocket pipeline is text-only by design — binary frames are not delivered to handlers or
 * interceptors (see {@code SummerWebSocketFrameHandler}, which accepts only {@code
 * TextWebSocketFrame}). The interceptor therefore receives a decoded {@code String}, not raw bytes.
 */
public interface WebSocketInterceptor {
    /**
     * Intercepts a WebSocket text message.
     *
     * @param ctx the WebSocket context
     * @param message the decoded text message received
     * @param chain the interceptor chain; call {@code proceed} to continue
     */
    void intercept(WebSocketContext ctx, String message, WebSocketInterceptorChain chain);
}
