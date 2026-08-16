package com.github.dropguard.summer.web.http;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.RadixTrie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance router implementation using a Radix Tree (Trie).
 *
 * <p>This implementation uses one {@link RadixTrie} per HTTP method for efficient path matching.
 * Path segment matching is performed at the byte level to minimize String allocations during
 * routing.
 *
 * <p>The route table is fixed at construction time: all routes are supplied via {@link
 * #RadixTreeHttpRouter(List)} (the {@link HttpRouter.Builder} collects routes and hands them over
 * in one step), and there is no registration method — once built, a router only serves {@link
 * #route(HttpContext)}. This makes a built router safe to share across threads: the trie map is
 * never mutated after construction.
 */
@Internal
public final class RadixTreeHttpRouter implements HttpRouter {

    private final Map<String, RadixTrie<Handler>> tries;

    /**
     * Creates a RadixTreeHttpRouter with the given routes, fixed for the router's lifetime.
     *
     * @param routes the routes to register
     */
    public RadixTreeHttpRouter(List<HttpRouter.Builder.Route> routes) {
        Map<String, RadixTrie<Handler>> built = new HashMap<>();
        for (HttpRouter.Builder.Route route : routes) {
            built.computeIfAbsent(route.method().name(), k -> new RadixTrie<>())
                    .insert(route.path(), route.handler());
        }
        this.tries = Map.copyOf(built);
    }

    /** Matches a request against the trie and dispatches to the appropriate handler. */
    @Override
    public void route(HttpContext ctx) throws Exception {
        HttpMethod method = ctx.request().getMethod();
        RadixTrie<Handler> trie = tries.get(method.name());
        if (trie == null) {
            return;
        }

        byte[] path = ctx.request().getRawPathBytes();
        RadixTrie.MatchResult<Handler> result = trie.match(path);
        if (result == null) {
            return;
        }

        result.params().forEach(ctx.request()::setPathParam);
        // Mark the match so the server layer can distinguish "no route" (404) from
        // "handler wrote no response" (500) — see HttpContext.markMatched().
        ctx.markMatched();
        result.handler().handle(ctx);
    }
}
