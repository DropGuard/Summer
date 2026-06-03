package summer.tck.web.ws;

import summer.web.WsRouter;
import summer.web.websocket.RadixWsRouter;

/**
 * TCK tests for RadixWsRouter implementation.
 */
public class RadixRouterWebSocketTCKTest extends AbstractWebSocketTCK {

	@Override
	protected WsRouter createRouter() {
		return new RadixWsRouter();
	}
}
