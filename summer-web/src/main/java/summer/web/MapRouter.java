package summer.web;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import summer.core.Component;
import summer.core.annotation.Replaces;
import summer.web.websocket.WebSocketHandler;

/**
 * Simple router implementation using a Map for route storage.
 *
 * <p>
 * This implementation prioritizes simplicity and readability over raw
 * performance. Use it for learning, testing, or applications where route
 * matching performance is not critical.
 * </p>
 *
 * <p>
 * To use this router instead of the default {@link Router}, add to your
 * configuration:
 * </p>
 *
 * <pre>{@code
 * @Configuration
 * public class WebConfig {
 * 	// MapRouter automatically replaces Router via @Replaces
 * }
 * }</pre>
 *
 * <p>
 * Route patterns support path parameters using curly braces (e.g.,
 * {@code /users/{id}}).
 * </p>
 */
@Replaces(RadixRouter.class)
@Component
public class MapRouter implements Router {

	/**
	 * Stores registered routes: "METHOD /path" -> RouteEntry
	 */
	private final Map<String, RouteEntry> routes = new HashMap<>();

	/**
	 * Stores WebSocket handlers: path -> WebSocketHandler
	 */
	private final Map<String, WebSocketHandler> wsHandlers = new HashMap<>();

	@Override
	public void register(String method, String path, Handler handler) {
		String key = method.toUpperCase() + " " + normalizePath(path);
		RouteEntry entry = parsePath(path);
		entry.handler = handler;
		routes.put(key, entry);
	}

	@Override
	public Object route(WebContext ctx) {
		String method = ctx.request().getMethod().toUpperCase();
		String path = ctx.request().getPath();

		// Try exact match first
		String key = method + " " + path;
		RouteEntry entry = routes.get(key);
		if (entry != null) {
			return entry.handler.handle(ctx);
		}

		// Try pattern matching
		for (Map.Entry<String, RouteEntry> route : routes.entrySet()) {
			if (!route.getKey().startsWith(method + " "))
				continue;

			RouteEntry candidate = route.getValue();
			Map<String, String> params = matchPattern(candidate.pattern, path);
			if (params != null) {
				params.forEach(ctx.request()::setAttribute);
				return candidate.handler.handle(ctx);
			}
		}

		return null;
	}

	@Override
	public void ws(String path, WebSocketHandler handler) {
		wsHandlers.put(normalizePath(path), handler);
	}

	@Override
	public Router.WsMatch routeWs(String path) {
		String normalizedPath = normalizePath(path);

		// Try exact match
		WebSocketHandler handler = wsHandlers.get(normalizedPath);
		if (handler != null) {
			return new Router.WsMatch(handler, new HashMap<>());
		}

		// Try pattern matching
		for (Map.Entry<String, WebSocketHandler> entry : wsHandlers.entrySet()) {
			RouteEntry routeEntry = parsePath(entry.getKey());
			Map<String, String> params = matchPattern(routeEntry.pattern, normalizedPath);
			if (params != null) {
				return new Router.WsMatch(entry.getValue(), params);
			}
		}

		return null;
	}

	/**
	 * Parses a path pattern into a RouteEntry with regex pattern.
	 */
	private RouteEntry parsePath(String path) {
		RouteEntry entry = new RouteEntry();
		String normalized = normalizePath(path);

		// Convert path pattern to regex: /users/{id} -> /users/([^/]+)
		StringBuilder regex = new StringBuilder();
		regex.append("^");
		String[] segments = normalized.split("/");
		for (String segment : segments) {
			if (segment.isEmpty())
				continue;
			regex.append("/");
			if (segment.startsWith("{") && segment.endsWith("}")) {
				String paramName = segment.substring(1, segment.length() - 1);
				entry.paramNames.add(paramName);
				regex.append("([^/]+)");
			} else {
				regex.append(Pattern.quote(segment));
			}
		}
		regex.append("$");

		entry.pattern = Pattern.compile(regex.toString());
		return entry;
	}

	/**
	 * Matches a path against a pattern and extracts path parameters.
	 *
	 * @return the extracted parameters, or null if no match
	 */
	private Map<String, String> matchPattern(Pattern pattern, String path) {
		Matcher matcher = pattern.matcher(path);
		if (!matcher.matches()) {
			return null;
		}

		RouteEntry entry = findEntryByPattern(pattern);
		if (entry == null) {
			return null;
		}

		Map<String, String> params = new HashMap<>();
		for (int i = 0; i < entry.paramNames.size(); i++) {
			params.put(entry.paramNames.get(i), matcher.group(i + 1));
		}
		return params;
	}

	/**
	 * Finds a RouteEntry by its pattern.
	 */
	private RouteEntry findEntryByPattern(Pattern pattern) {
		for (RouteEntry entry : routes.values()) {
			if (entry.pattern.equals(pattern)) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * Normalizes a path by ensuring it starts with / and doesn't end with /.
	 */
	private String normalizePath(String path) {
		if (path == null || path.isEmpty()) {
			return "/";
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		if (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	/**
	 * Internal route entry.
	 */
	private static class RouteEntry {
		Pattern pattern;
		Handler handler;
		java.util.List<String> paramNames = new java.util.ArrayList<>();
	}
}
