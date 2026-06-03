package summer.web.websocket;

import java.util.HashMap;
import java.util.Map;
import summer.core.Component;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;
import summer.web.AbstractMapRouter;
import summer.web.WsRouter;

/**
 * Simple WebSocket router implementation using a Map for route storage.
 *
 * <p>
 * This implementation prioritizes simplicity and readability over raw
 * performance. It is activated only when the reflection-based DI engine
 * ({@link RuntimeDiMarker}) is present.
 * </p>
 */
@ConditionalOnBean(RuntimeDiMarker.class)
@Replaces(RadixWsRouter.class)
@Component
public class MapWsRouter extends AbstractMapRouter implements WsRouter {

	private final Map<String, RouteEntryWithHandler> routes = new HashMap<>();

	@Override
	public void ws(String path, WebSocketHandler handler) {
		RouteEntryWithHandler entry = new RouteEntryWithHandler();
		RouteEntry base = parsePath(path);
		entry.pattern = base.pattern;
		entry.paramNames = base.paramNames;
		entry.handler = handler;
		routes.put(normalizePath(path), entry);
	}

	@Override
	public WsMatch routeWs(String path) {
		String normalized = normalizePath(path);

		RouteEntryWithHandler entry = routes.get(normalized);
		if (entry != null) {
			return new WsMatch(entry.handler, Map.of());
		}

		for (RouteEntryWithHandler route : routes.values()) {
			Map<String, String> params = matchPattern(route, normalized);
			if (params != null) {
				return new WsMatch(route.handler, params);
			}
		}

		return null;
	}

	private static class RouteEntryWithHandler extends RouteEntry {
		WebSocketHandler handler;
	}
}
