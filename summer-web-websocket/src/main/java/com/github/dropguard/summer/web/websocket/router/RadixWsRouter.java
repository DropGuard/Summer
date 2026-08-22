package com.github.dropguard.summer.web.websocket.router;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.RadixTrie;
import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.WebSocketHandler;
import java.util.List;

/**
 * WebSocket router implementation using a Radix Tree (Trie) for path matching.
 *
 * <p>Provides WebSocket-specific routing with support for path parameters and wildcard patterns
 * ({@code *} and {@code **}).
 *
 * <p>This router is immutable — routes are provided at construction time.
 *
 * <p>Lives in the {@code websocket.router} sub-package (not {@code web.websocket}) so the
 * `com.github.dropguard.summer.web.websocket` package is owned by exactly one jar — the interface
 * contract stays in {@code summer-web}, implementations in {@code summer-web-websocket}, keeping
 * the deployment JPMS-clean (no split package).
 */
@Internal
public class RadixWsRouter implements WsRouter {

    private final RadixTrie<WebSocketHandler> trie = new RadixTrie<>();

    /**
     * Creates an immutable RadixWsRouter from the given routes.
     *
     * @param routes the routes to register
     */
    public RadixWsRouter(List<WsRoute> routes) {
        for (WsRoute route : routes) {
            trie.insert(route.path(), route.handler());
        }
    }

    @Override
    public WsMatch routeWs(String pathStr) {
        RadixTrie.MatchResult<WebSocketHandler> result = trie.match(pathStr);
        if (result == null) {
            return null;
        }
        return new WsMatch(result.handler(), result.params());
    }
}
