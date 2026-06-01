package summer.web;

import summer.web.websocket.WebSocketHandler;

/**
 * Interface for HTTP request routing.
 *
 * <p>
 * Implementations provide different routing strategies (e.g., radix tree,
 * regex-based map). The router maps incoming HTTP requests to their
 * corresponding handlers based on method and path.
 * </p>
 */
public interface Router {

	/**
	 * Registers a handler for the given HTTP method and path pattern.
	 *
	 * @param method  the HTTP method (GET, POST, PUT, DELETE, etc.)
	 * @param path    the path pattern (e.g., "/users/{id}")
	 * @param handler the request handler
	 */
	void register(String method, String path, Handler handler);

	/**
	 * Registers a GET handler for the given path pattern.
	 *
	 * @param path    the path pattern
	 * @param handler the request handler
	 */
	default void get(String path, Handler handler) {
		register("GET", path, handler);
	}

	/**
	 * Registers a POST handler for the given path pattern.
	 *
	 * @param path    the path pattern
	 * @param handler the request handler
	 */
	default void post(String path, Handler handler) {
		register("POST", path, handler);
	}

	/**
	 * Registers a PUT handler for the given path pattern.
	 *
	 * @param path    the path pattern
	 * @param handler the request handler
	 */
	default void put(String path, Handler handler) {
		register("PUT", path, handler);
	}

	/**
	 * Registers a DELETE handler for the given path pattern.
	 *
	 * @param path    the path pattern
	 * @param handler the request handler
	 */
	default void delete(String path, Handler handler) {
		register("DELETE", path, handler);
	}

	/**
	 * Routes an incoming request to the appropriate handler.
	 *
	 * @param ctx the web context containing the request
	 * @return the handler result, or null if no route matches
	 */
	Object route(WebContext ctx);

	/**
	 * Registers a WebSocket handler for the given path pattern.
	 *
	 * @param path    the path pattern
	 * @param handler the WebSocket handler
	 */
	void ws(String path, WebSocketHandler handler);

	/**
	 * Routes a WebSocket upgrade request to the appropriate handler.
	 *
	 * @param path the request path
	 * @return the WebSocket match containing handler and path parameters, or null
	 *         if no route matches
	 */
	WsMatch routeWs(String path);

	/**
	 * Represents the result of matching a WebSocket route.
	 */
	class WsMatch {
		public final WebSocketHandler handler;
		public final java.util.Map<String, String> pathParams;

		public WsMatch(WebSocketHandler handler, java.util.Map<String, String> pathParams) {
			this.handler = handler;
			this.pathParams = pathParams;
		}
	}
}
