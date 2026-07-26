package com.github.dropguard.summer.twitter.config;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.dm.DmHandler;
import com.github.dropguard.summer.twitter.ws.EventsHandler;
import com.github.dropguard.summer.web.WsRouteProvider;
import com.github.dropguard.summer.web.WsRouter;

@Component
public class WebSocketConfig implements WsRouteProvider {
    
    private final EventsHandler eventsHandler;
    private final DmHandler dmHandler;

    public WebSocketConfig(EventsHandler eventsHandler, DmHandler dmHandler) {
        this.eventsHandler = eventsHandler;
        this.dmHandler = dmHandler;
    }

    @Override
    public void provide(WsRouter.Builder builder) {
        builder.ws("/ws/events", eventsHandler);
        builder.ws("/ws/dm", dmHandler);
    }
}
