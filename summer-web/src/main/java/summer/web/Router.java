package summer.web;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;

/**
 * Simple router implementation that maps HTTP requests to handlers.
 */
@Component
public class Router {
	private final Map<RouteKey, RouteHandler> routes = new HashMap<>();

	public void register(String method, String path, RouteHandler handler) {
		routes.put(new RouteKey(method, path), handler);
	}

	public void get(String path, RouteHandler handler) {
		register("GET", path, handler);
	}

	public void post(String path, RouteHandler handler) {
		register("POST", path, handler);
	}

	public void put(String path, RouteHandler handler) {
		register("PUT", path, handler);
	}

	public void delete(String path, RouteHandler handler) {
		register("DELETE", path, handler);
	}

	public Object route(Request request, Response response) {
		for (Map.Entry<RouteKey, RouteHandler> entry : routes.entrySet()) {
			RouteKey routeKey = entry.getKey();
			if (matches(routeKey, request)) {
				return entry.getValue().handle(request, response);
			}
		}
		return null;
	}

	private boolean matches(RouteKey routeKey, Request request) {
		if (!routeKey.method.equals(request.getMethod())) {
			return false;
		}

		// Path pattern matching with variables (e.g., /users/{id})
		String[] routeParts = routeKey.path.split("/");
		String[] requestParts = request.getPath().split("/");

		if (routeParts.length != requestParts.length) {
			return false;
		}

		for (int i = 0; i < routeParts.length; i++) {
			String routePart = routeParts[i];
			String requestPart = requestParts[i];

			// Check if it's a variable part
			if (routePart.startsWith("{") && routePart.endsWith("}")) {
				// Store variable value in request attributes
				String paramName = routePart.substring(1, routePart.length() - 1);
				request.setAttribute(paramName, requestPart);
			} else if (!routePart.equals(requestPart)) {
				// Not a variable and parts don't match
				return false;
			}
		}

		return true;
	}

	private static class RouteKey {
		private final String method;
		private final String path;

		RouteKey(String method, String path) {
			this.method = method;
			this.path = normalizePath(path);
		}

		private String normalizePath(String path) {
			if (!path.startsWith("/")) {
				return "/" + path;
			}
			return path;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			RouteKey routeKey = (RouteKey) o;
			return method.equals(routeKey.method) && path.equals(routeKey.path);
		}

		@Override
		public int hashCode() {
			return 31 * method.hashCode() + path.hashCode();
		}

		@Override
		public String toString() {
			return method + " " + path;
		}
	}

	@FunctionalInterface
	public interface RouteHandler {
		Object handle(Request request, Response response);
	}
}