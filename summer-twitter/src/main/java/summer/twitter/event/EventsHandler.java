package summer.twitter.event;

import io.jsonwebtoken.Claims;
import summer.core.Component;
import summer.twitter.auth.JwtUtil;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WebSocketHandler;

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
        String token = ctx.header("sec-websocket-protocol");
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
