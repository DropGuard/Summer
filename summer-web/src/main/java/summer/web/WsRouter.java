package summer.web;

import java.util.Map;
import summer.web.websocket.WebSocketHandler;

/**
 * Interface for WebSocket routing.
 *
 * <p>
 * Implementations provide WebSocket route matching and path parameter
 * extraction.
 * </p>
 */
public interface WsRouter {

	/**
	 * Registers a WebSocket handler for the given path pattern.
	 *
	 * @param path
	 *            the path pattern
	 * @param handler
	 *            the WebSocket handler
	 */
	void ws(String path, WebSocketHandler handler);

	/**
	 * Routes a WebSocket upgrade request to the appropriate handler.
	 *
	 * @param path
	 *            the request path
	 * @return the WebSocket match containing handler and path parameters, or null
	 *         if no route matches
	 */
	WsMatch routeWs(String path);

	/**
	 * Represents the result of matching a WebSocket route.
	 */
	class WsMatch {
		public final WebSocketHandler handler;
		public final Map<String, String> pathParams;

		public WsMatch(WebSocketHandler handler, Map<String, String> pathParams) {
			this.handler = handler;
			this.pathParams = pathParams;
		}
	}
}
