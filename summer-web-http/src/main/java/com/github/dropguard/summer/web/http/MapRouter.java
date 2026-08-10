package com.github.dropguard.summer.web.http;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.PathMatcher;
import com.github.dropguard.summer.web.PathMatcher.RouteEntry;
import com.github.dropguard.summer.web.PathUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable router implementation using a Map for route storage.
 *
 * <p>This implementation prioritizes simplicity and readability over raw performance. Routes are
 * provided at construction time via {@link HttpRouter.Builder.Route}s, making the router truly
 * immutable and thread-safe.
 *
 * <p>Route patterns support path parameters using curly braces (e.g., {@code /users/{id}}).
 */
@Internal
public class MapRouter implements HttpRouter {

    private final Map<String, RouteEntryWithHandler> routes;

    /**
     * Creates an immutable MapRouter from the given routes.
     *
     * @param routes the routes to build the routing table from
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
            // Mark the match so the server layer can distinguish "no route" (404) from
            // "handler wrote no response" (500) — see HttpContext.markMatched().
            ctx.markMatched();
            entry.handler.handle(ctx);
            return;
        }

        for (Map.Entry<String, RouteEntryWithHandler> route : routes.entrySet()) {
            if (!route.getKey().startsWith(method + " ")) continue;

            Map<String, String> params = PathMatcher.matchPattern(route.getValue(), path);
            if (params != null) {
                params.forEach(ctx.request()::setPathParam);
                ctx.markMatched();
                route.getValue().handler.handle(ctx);
                return;
            }
        }
    }

    private static class RouteEntryWithHandler extends RouteEntry {
        Handler handler;
    }
}
