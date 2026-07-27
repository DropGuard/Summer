package com.github.dropguard.summer.web.websocket;

/** Represents the chain of execution for a WebSocket message. */
public interface WsFilterChain {
    /**
     * Passes the message to the next interceptor in the chain, or to the final WebSocket handler if
     * there are no more interceptors.
     *
     * @param ctx the WebSocket context
     * @param message the text message received
     */
    void doFilter(WebSocketContext ctx, String message);
}
