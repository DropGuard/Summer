package com.github.dropguard.summer.tck.web.ws;

import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.RadixWsRouter;

/**
 * TCK tests for RadixWsRouter implementation.
 */
public class RadixRouterWebSocketTCKTest extends AbstractWebSocketTCK {

	@Override
	protected WsRouter.Builder createBuilder() {
		return new WsRouter.Builder(RadixWsRouter::new);
	}
}
