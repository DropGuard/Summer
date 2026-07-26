package com.github.dropguard.summer.web.websocket;

/**
 * Interceptor for WebSocket messages. Allows executing logic before and after a
 * WebSocket message is processed by the handler.
 */
public interface WsInterceptor {
	/**
	 * Intercepts a WebSocket message.
	 *
	 * @param ctx
	 *            the WebSocket context
	 * @param message
	 *            the text message received
	 * @param chain
	 *            the filter chain
	 */
	void intercept(WebSocketContext ctx, String message, WsFilterChain chain);
}
