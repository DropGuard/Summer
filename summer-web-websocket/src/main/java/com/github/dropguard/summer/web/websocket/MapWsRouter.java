package com.github.dropguard.summer.web.websocket;

import com.github.dropguard.summer.web.PathMatcher;
import com.github.dropguard.summer.web.PathMatcher.RouteEntry;
import com.github.dropguard.summer.web.PathUtils;
import com.github.dropguard.summer.web.WsRouter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple WebSocket router implementation using a Map for route storage.
 *
 * <p>This implementation prioritizes simplicity and readability over raw performance. Routes are
 * provided at construction time, making the router truly immutable and thread-safe.
 */
public class MapWsRouter implements WsRouter {

    private final Map<String, RouteEntryWithHandler> routes;

    /**
     * Creates an immutable MapWsRouter from the given routes.
     *
     * @param wsRoutes the routes to build the routing table from
     */
    public MapWsRouter(List<WsRouter.WsRoute> wsRoutes) {
        this.routes = new HashMap<>(wsRoutes.size());
        for (WsRouter.WsRoute route : wsRoutes) {
            RouteEntryWithHandler entry = new RouteEntryWithHandler();
            RouteEntry base = PathMatcher.parsePath(route.path());
            entry.pattern = base.pattern;
            entry.paramNames = base.paramNames;
            entry.handler = route.handler();
            routes.put(PathUtils.normalizePath(route.path()), entry);
        }
    }

    @Override
    public WsMatch routeWs(String path) {
        String normalized = PathUtils.normalizePath(path);

        RouteEntryWithHandler entry = routes.get(normalized);
        if (entry != null) {
            return new WsMatch(entry.handler, Map.of());
        }

        for (RouteEntryWithHandler route : routes.values()) {
            Map<String, String> params = PathMatcher.matchPattern(route, normalized);
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
