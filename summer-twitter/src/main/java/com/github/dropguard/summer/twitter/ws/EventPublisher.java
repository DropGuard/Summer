package com.github.dropguard.summer.twitter.ws;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.websocket.WebSocketContext;

@Component
public class EventPublisher {

    public void publish(Long userId, Object event) {
        WebSocketContext ctx = EventsHandler.SESSIONS.get(userId);
        if (ctx != null) {
            ctx.sendJson(event);
        }
    }
}
