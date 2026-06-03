package summer.web;

/**
 * Interface for HTTP request routing.
 *
 * <p>
 * Implementations provide different routing strategies (e.g., radix tree,
 * regex-based map). The router maps incoming HTTP requests to their
 * corresponding handlers based on method and path.
 * </p>
 */
public interface HttpRouter {

	/**
	 * Registers a handler for the given HTTP method and path pattern.
	 *
	 * @param method
	 *                the HTTP method (GET, POST, PUT, DELETE, etc.)
	 * @param path
	 *                the path pattern (e.g., "/users/{id}")
	 * @param handler
	 *                the request handler
	 */
	void register(String method, String path, Handler handler);

	/**
	 * Registers a GET handler for the given path pattern.
	 */
	default void get(String path, Handler handler) {
		register("GET", path, handler);
	}

	/**
	 * Registers a POST handler for the given path pattern.
	 */
	default void post(String path, Handler handler) {
		register("POST", path, handler);
	}

	/**
	 * Registers a PUT handler for the given path pattern.
	 */
	default void put(String path, Handler handler) {
		register("PUT", path, handler);
	}

	/**
	 * Registers a DELETE handler for the given path pattern.
	 */
	default void delete(String path, Handler handler) {
		register("DELETE", path, handler);
	}

	/**
	 * Routes an incoming request to the appropriate handler.
	 *
	 * @param ctx
	 *            the web context containing the request
	 * @return the handler result, or null if no route matches
	 */
	Object route(HttpContext ctx);
}
