package summer.web.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import summer.web.Handler;
import summer.web.HttpContext;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.PathMatcher;
import summer.web.PathMatcher.RouteEntry;
import summer.web.PathUtils;

/**
 * Immutable router implementation using a Map for route storage.
 *
 * <p>
 * This implementation prioritizes simplicity and readability over raw
 * performance. Routes are provided at construction time via
 * {@link HttpRouter.Builder.Route}s, making the router truly immutable and
 * thread-safe.
 * </p>
 *
 * <p>
 * Route patterns support path parameters using curly braces (e.g.,
 * {@code /users/{id}}).
 * </p>
 */
public class MapRouter implements HttpRouter {

	private final Map<String, RouteEntryWithHandler> routes;

	/**
	 * Creates an immutable MapRouter from the given routes.
	 *
	 * @param routes
	 *            the routes to build the routing table from
	 */
	public MapRouter(List<HttpRouter.Builder.Route> routes) {
		this.routes = new HashMap<>(routes.size());
		for (HttpRouter.Builder.Route route : routes) {
			String key = route.method() + " " + PathUtils.normalizePath(route.path());
			RouteEntryWithHandler entry = new RouteEntryWithHandler();
			RouteEntry base = PathMatcher.parsePath(route.path());
			entry.pattern = base.pattern;
			entry.paramNames = base.paramNames;
			entry.handler = route.handler();
			this.routes.put(key, entry);
		}
	}

	@Override
	public void route(HttpContext ctx) {
		HttpMethod method = ctx.request().getMethod();
		String path = PathUtils.normalizePath(ctx.request().getPath());

		String key = method + " " + path;
		RouteEntryWithHandler entry = routes.get(key);
		if (entry != null) {
			entry.handler.handle(ctx);
			return;
		}

		for (Map.Entry<String, RouteEntryWithHandler> route : routes.entrySet()) {
			if (!route.getKey().startsWith(method + " "))
				continue;

			Map<String, String> params = PathMatcher.matchPattern(route.getValue(), path);
			if (params != null) {
				params.forEach(ctx.request()::setPathParam);
				route.getValue().handler.handle(ctx);
				return;
			}
		}
	}

	private static class RouteEntryWithHandler extends RouteEntry {
		Handler handler;
	}
}
