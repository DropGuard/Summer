package summer.web.websocket;

import java.util.List;
import summer.web.RadixTrie;
import summer.web.WsRouter;

/**
 * WebSocket router implementation using a Radix Tree (Trie) for path matching.
 *
 * <p>
 * Provides WebSocket-specific routing with support for path parameters and
 * wildcard patterns ({@code *} and {@code **}).
 * </p>
 *
 * <p>
 * This router is immutable — routes are provided at construction time.
 * </p>
 */
public class RadixWsRouter implements WsRouter {

	private final RadixTrie<WebSocketHandler> trie = new RadixTrie<>();

	/**
	 * Creates an immutable RadixWsRouter from the given routes.
	 *
	 * @param routes
	 *            the routes to register
	 */
	public RadixWsRouter(List<WsRoute> routes) {
		for (WsRoute route : routes) {
			trie.insert(route.path(), route.handler());
		}
	}

	@Override
	public WsMatch routeWs(String pathStr) {
		byte[] path = pathStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		RadixTrie.MatchResult<WebSocketHandler> result = trie.match(path);
		if (result == null) {
			return null;
		}
		return new WsMatch(result.handler(), result.params());
	}
}
