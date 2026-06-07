package summer.tck.web.ws;

import summer.web.WsRouter;
import summer.web.websocket.RadixWsRouter;

/**
 * TCK tests for RadixWsRouter implementation.
 */
public class RadixRouterWebSocketTCKTest extends AbstractWebSocketTCK {

	@Override
	protected WsRouter.Builder createBuilder() {
		return new WsRouter.Builder(RadixWsRouter::new);
	}
}
