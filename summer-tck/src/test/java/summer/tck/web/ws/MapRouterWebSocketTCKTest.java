package summer.tck.web.ws;

import summer.web.WsRouter;
import summer.web.websocket.MapWsRouter;

/**
 * TCK tests for MapWsRouter implementation.
 */
public class MapRouterWebSocketTCKTest extends AbstractWebSocketTCK {

	@Override
	protected WsRouter createRouter() {
		return new MapWsRouter();
	}
}
