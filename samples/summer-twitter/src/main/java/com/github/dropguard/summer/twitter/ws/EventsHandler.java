package com.github.dropguard.summer.twitter.ws;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.auth.JwtUtil;
import com.github.dropguard.summer.web.websocket.WebSocketContext;
import com.github.dropguard.summer.web.websocket.WebSocketHandler;
import io.jsonwebtoken.Claims;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class EventsHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;
    public static final ConcurrentMap<Long, WebSocketContext> SESSIONS = new ConcurrentHashMap<>();

    public EventsHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void handle(WebSocketContext ctx) {
        String auth = ctx.header("authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        if (token == null || token.isEmpty()) {
            ctx.close();
            return;
        }

        try {
            Claims claims = jwtUtil.extractClaims(token);
            Long userId = Long.valueOf(claims.getSubject());

            SESSIONS.put(userId, ctx);

            ctx.onClose(() -> SESSIONS.remove(userId, ctx));
        } catch (Exception e) {
            ctx.close();
        }
    }
}
