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
            entry.catchAll = base.catchAll;
            entry.handler = route.handler();
            this.routes.put(key, entry);
        }
    }

    @Override
    public void route(HttpContext ctx) throws Exception {
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

        // Among the pattern-based routes that match, pick the most specific one. The map iteration
        // order is undefined, so we do NOT return on the first regex hit — instead we rank the
        // candidates (path parameter > single-segment wildcard > catch-all) and keep the best,
        // matching the Radix router's documented priority contract.
        RouteEntryWithHandler best = null;
        Map<String, String> bestParams = null;
        int bestRank = Integer.MIN_VALUE;
        for (Map.Entry<String, RouteEntryWithHandler> route : routes.entrySet()) {
            if (!route.getKey().startsWith(method + " ")) continue;

            Map<String, String> params = PathMatcher.matchPattern(route.getValue(), path);
            if (params == null) continue;

            int rank = routeRank(route.getValue());
            if (rank > bestRank) {
                bestRank = rank;
                best = route.getValue();
                bestParams = params;
            }
        }
        if (best != null) {
            bestParams.forEach(ctx.request()::setPathParam);
            ctx.markMatched();
            best.handler.handle(ctx);
        }
    }

    /**
     * Priority rank for a matching pattern: higher wins. Path parameter ({@code {name}}) ranks
     * above the single-segment wildcard ({@code *}), which ranks above the multi-segment catch-all
     * ({@code **}). This mirrors {@code RadixTrie}'s matching contract.
     */
    private static int routeRank(RouteEntry entry) {
        if (entry.catchAll) {
            return 1; // ** — last resort
        }
        if (!entry.paramNames.isEmpty()) {
            return 3; // {name}
        }
        return 2; // * (no params, not catch-all)
    }

    private static class RouteEntryWithHandler extends RouteEntry {
        Handler handler;
    }
}
