package summer.twitter.dm;

import io.jsonwebtoken.Claims;
import summer.core.Component;
import summer.twitter.auth.JwtUtil;
import summer.twitter.user.User;
import summer.twitter.user.UserRepository;
import summer.web.websocket.WebSocketContext;
import summer.web.websocket.WebSocketHandler;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DmHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;
    private final DmRepository dmRepository;
    private final UserRepository userRepository;
    
    public static final ConcurrentMap<Long, WebSocketContext> SESSIONS = new ConcurrentHashMap<>();

    public DmHandler(JwtUtil jwtUtil, DmRepository dmRepository, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.dmRepository = dmRepository;
        this.userRepository = userRepository;
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
            String username = claims.get("username", String.class);
            
            SESSIONS.put(userId, ctx);
            
            ctx.onMessageAs(Map.class, msg -> {
                String type = (String) msg.get("type");
                if ("send".equals(type)) {
                    String toUsername = (String) msg.get("to");
                    String text = (String) msg.get("text");
                    handleSend(userId, username, toUsername, text, ctx);
                } else if ("mark_read".equals(type)) {
                    String fromUsername = (String) msg.get("from");
                    handleMarkRead(userId, username, fromUsername, ctx);
                }
            });
            
            ctx.onClose(() -> SESSIONS.remove(userId, ctx));
        } catch (Exception e) {
            ctx.close();
        }
    }
    
    private void handleSend(Long senderId, String senderUsername, String toUsername, String text, WebSocketContext ctx) {
        Optional<User> toUserOpt = userRepository.findByUsername(toUsername);
        if (toUserOpt.isEmpty()) {
            // Recipient does not exist: report the error to the sender instead of
            // silently dropping the message (a silent drop makes the sender believe
            // delivery succeeded). The message is not persisted.
            ctx.sendJson(Map.of(
                "type", "error",
                "code", "user_not_found",
                "message", "recipient @" + toUsername + " does not exist"
            ));
            return;
        }

        User toUser = toUserOpt.get();
        OffsetDateTime now = OffsetDateTime.now();

        DirectMessage msg = new DirectMessage(null, senderId, toUser.id(), text, null, now);
        msg = dmRepository.insertMessage(msg);
        dmRepository.upsertConversation(senderId, toUser.id(), now);

        WebSocketContext toCtx = SESSIONS.get(toUser.id());
        if (toCtx != null) {
            toCtx.sendJson(Map.of(
                "type", "receive",
                "messageId", String.valueOf(msg.id()),
                "from", senderUsername,
                "text", text,
                "timestamp", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ));
        }
    }
    
    private void handleMarkRead(Long receiverId, String receiverUsername, String fromUsername, WebSocketContext ctx) {
        Optional<User> fromUserOpt = userRepository.findByUsername(fromUsername);
        if (fromUserOpt.isEmpty()) return;
        
        User fromUser = fromUserOpt.get();
        OffsetDateTime now = OffsetDateTime.now();
        
        dmRepository.markAsRead(fromUser.id(), receiverId, now);
        String timeStr = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        
        // Notify the person who marked read
        ctx.sendJson(Map.of(
            "type", "read_receipt",
            "conversationWith", fromUsername,
            "readAt", timeStr
        ));
        
        // Notify the sender that their messages were read
        WebSocketContext fromCtx = SESSIONS.get(fromUser.id());
        if (fromCtx != null) {
            fromCtx.sendJson(Map.of(
                "type", "read_receipt",
                "conversationWith", receiverUsername,
                "readAt", timeStr
            ));
        }
    }
    
}
