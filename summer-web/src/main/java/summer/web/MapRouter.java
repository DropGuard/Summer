package summer.web;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import summer.core.Component;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.ConditionalOnBean;
import summer.web.websocket.WebSocketHandler;

/**
 * Simple router implementation using a Map for route storage.
 *
 * <p>
 * This implementation prioritizes simplicity and readability over raw
 * performance. It is activated only when the reflection-based DI engine
 * ({@link RuntimeDiMarker}) is present. Use it for learning, testing, or
 * applications where route matching performance is not critical.
 * </p>
 *
 * <p>
 * Route patterns support path parameters using curly braces (e.g.,
 * {@code /users/{id}}).
 * </p>
 */
@ConditionalOnBean(RuntimeDiMarker.class)
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
		String path = normalizePath(ctx.request().getPath());

		// Try exact match first
		String key = method + " " + path;
		RouteEntry entry = routes.get(key);
		if (entry != null) {
			return entry.handler.handle(ctx);
		}

		// Try pattern matching (only routes for this method)
		for (Map.Entry<String, RouteEntry> route : routes.entrySet()) {
			if (!route.getKey().startsWith(method + " "))
				continue;

			Map<String, String> params = matchPattern(route.getValue(), path);
			if (params != null) {
				params.forEach(ctx.request()::setAttribute);
				return route.getValue().handler.handle(ctx);
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
			Map<String, String> params = matchPattern(routeEntry, normalizedPath);
			if (params != null) {
				return new Router.WsMatch(entry.getValue(), params);
			}
		}

		return null;
	}

	/**
	 * Parses a path pattern into a RouteEntry with regex pattern.
	 *
	 * <p>
	 * Supported patterns:
	 * </p>
	 * <ul>
	 * <li>{@code /users/{id}} - path parameter</li>
	 * <li>{@code /files/*} - single segment wildcard</li>
	 * <li>{@code /api/**} - multi-segment wildcard (matches rest of path)</li>
	 * </ul>
	 */
	private RouteEntry parsePath(String path) {
		RouteEntry entry = new RouteEntry();
		String normalized = normalizePath(path);

		// Convert path pattern to regex
		// /users/{id} -> /users/([^/]+)
		// /files/* -> /files/([^/]+)
		// /api/** -> /api/(.*)
		StringBuilder regex = new StringBuilder();
		regex.append("^/?");
		String[] segments = normalized.split("/");
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (segment.isEmpty())
				continue;
			if (i > 0 || regex.length() > 2) {
				regex.append("/");
			}
			if ("**".equals(segment)) {
				// Multi-segment wildcard: matches everything including /
				regex.append("(.*)");
				// ** must be last segment
				break;
			} else if ("*".equals(segment)) {
				// Single-segment wildcard: matches one segment (not /)
				regex.append("([^/]+)");
			} else if (segment.startsWith("{") && segment.endsWith("}")) {
				String paramName = segment.substring(1, segment.length() - 1);
				entry.paramNames.add(paramName);
				regex.append("([^/]+)");
			} else {
				regex.append(Pattern.quote(segment));
			}
		}
		regex.append("/?$");

		entry.pattern = Pattern.compile(regex.toString());
		return entry;
	}

	/**
	 * Matches a path against a pattern and extracts path parameters. URL-decodes
	 * parameter values.
	 *
	 * @param entry
	 *            the RouteEntry containing pattern and param names
	 * @param path
	 *            the normalized request path
	 * @return the extracted parameters, or null if no match
	 */
	private Map<String, String> matchPattern(RouteEntry entry, String path) {
		Matcher matcher = entry.pattern.matcher(path);
		if (!matcher.matches()) {
			return null;
		}

		Map<String, String> params = new HashMap<>();
		for (int i = 0; i < entry.paramNames.size(); i++) {
			String raw = matcher.group(i + 1);
			String decoded = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
			params.put(entry.paramNames.get(i), decoded);
		}
		return params;
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
