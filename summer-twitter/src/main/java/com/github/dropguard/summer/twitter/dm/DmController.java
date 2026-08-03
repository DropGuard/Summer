package com.github.dropguard.summer.twitter.dm;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.twitter.user.User;
import com.github.dropguard.summer.twitter.user.UserRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoints for DM history.
 *
 * <p>Real-time messaging is handled by {@link DmHandler} via WebSocket; these
 * endpoints let clients load conversation lists and scroll back through message
 * history — the two read paths that are impossible over a pure push channel.
 */
@RestController
public class DmController {

    private final DmRepository dmRepository;
    private final UserRepository userRepository;

    public DmController(DmRepository dmRepository, UserRepository userRepository) {
        this.dmRepository = dmRepository;
        this.userRepository = userRepository;
    }

    /** Lightweight conversation summary sent to the client. */
    public record ConversationResponse(Long withUserId, String withUsername, String withDisplayName,
                                       Long conversationId, String lastMessageAt) {}

    @Get("/api/dm/conversations")
    public void listConversations(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        List<Conversation> conversations = dmRepository.findConversations(currentUserId);

        List<ConversationResponse> result = new ArrayList<>();
        for (Conversation c : conversations) {
            Long otherId = c.userOneId().equals(currentUserId) ? c.userTwoId() : c.userOneId();
            Optional<User> otherUser = userRepository.findById(otherId);
            if (otherUser.isEmpty()) continue;
            User u = otherUser.get();
            result.add(new ConversationResponse(
                    u.id(), u.username(), u.displayName(),
                    c.id(), c.lastMessageAt().toString()));
        }
        ctx.ok(result);
    }

    @Get("/api/dm/messages")
    public void listMessages(HttpContext ctx) {
        Long currentUserId = ctx.request().getAttribute(RequestAttributes.USER_ID);
        String withParam = ctx.request().queryParam("with");
        if (withParam == null || withParam.isEmpty()) {
            ctx.ok(List.of());
            return;
        }

        Long withUserId;
        try {
            withUserId = Long.parseLong(withParam);
        } catch (NumberFormatException e) {
            ctx.ok(List.of());
            return;
        }

        String cursorStr = ctx.request().queryParam("cursor");
        Long cursor = cursorStr != null ? Long.parseLong(cursorStr) : null;

        String limitStr = ctx.request().queryParam("limit");
        int limit = limitStr != null ? Integer.parseInt(limitStr) : 50;
        if (limit > 100) limit = 100;

        List<DirectMessage> messages = dmRepository.findMessages(currentUserId, withUserId, cursor, limit);
        ctx.ok(messages);
    }
}
