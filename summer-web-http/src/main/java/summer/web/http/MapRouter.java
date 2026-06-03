package summer.web.http;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;
import summer.web.AbstractMapRouter;
import summer.web.Handler;
import summer.web.HttpRouter;
import summer.web.HttpContext;

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
@Replaces(RadixRouter.class)
@Component
public class MapRouter extends AbstractMapRouter implements HttpRouter {

	private final Map<String, RouteEntryWithHandler> routes = new HashMap<>();

	@Override
	public void register(String method, String path, Handler handler) {
		String key = method.toUpperCase() + " " + normalizePath(path);
		RouteEntryWithHandler entry = new RouteEntryWithHandler();
		RouteEntry base = parsePath(path);
		entry.pattern = base.pattern;
		entry.paramNames = base.paramNames;
		entry.handler = handler;
		routes.put(key, entry);
	}

	@Override
	public Object route(HttpContext ctx) {
		String method = ctx.request().getMethod().toUpperCase();
		String path = normalizePath(ctx.request().getPath());

		String key = method + " " + path;
		RouteEntryWithHandler entry = routes.get(key);
		if (entry != null) {
			return entry.handler.handle(ctx);
		}

		for (Map.Entry<String, RouteEntryWithHandler> route : routes.entrySet()) {
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

	private static class RouteEntryWithHandler extends RouteEntry {
		Handler handler;
	}
}
