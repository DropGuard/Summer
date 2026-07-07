package summer.twitter.event;

import summer.core.Component;
import summer.web.websocket.WebSocketContext;

@Component
public class EventPublisher {

    public void publish(Long userId, Object event) {
        WebSocketContext ctx = EventsHandler.SESSIONS.get(userId);
        if (ctx != null) {
            ctx.sendJson(event);
        }
    }
}
