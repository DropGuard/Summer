package com.github.dropguard.summer.tck.web.ws;

import com.github.dropguard.summer.web.WsRouter;
import com.github.dropguard.summer.web.websocket.MapWsRouter;

/** TCK tests for MapWsRouter implementation. */
public class MapRouterWebSocketTCKTest extends AbstractWebSocketTCK {

    @Override
    protected WsRouter.Builder createBuilder() {
        return new WsRouter.Builder(MapWsRouter::new);
    }
}
