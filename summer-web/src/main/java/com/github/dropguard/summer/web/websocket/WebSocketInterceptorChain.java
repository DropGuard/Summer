package com.github.dropguard.summer.web.websocket;

/**
 * The chain of execution for a WebSocket text message. Mirrors the AOP chain idiom ({@code
 * chain.proceed()}): each {@link WebSocketInterceptor} either continues the chain via {@link
 * #proceed} or short-circuits it.
 */
public interface WebSocketInterceptorChain {
    /**
     * Passes the message to the next interceptor in the chain, or to the final WebSocket handler if
     * there are no more interceptors.
     *
     * @param ctx the WebSocket context
     * @param message the text message received (may have been transformed by earlier interceptors)
     */
    void proceed(WebSocketContext ctx, String message);
}
