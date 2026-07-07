package summer.twitter.config;

import summer.core.Component;
import summer.twitter.dm.DmHandler;
import summer.twitter.event.EventsHandler;
import summer.web.WsRouteProvider;
import summer.web.WsRouter;

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
