package summer.twitter.dm;

import summer.core.Component;
import summer.data.jdbc.JdbcTemplate;
import summer.twitter.infra.SnowflakeIdGenerator;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class DmRepository {
    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;

    public DmRepository(JdbcTemplate jdbcTemplate, SnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public DirectMessage insertMessage(DirectMessage msg) {
        Long id = msg.id() != null ? msg.id() : idGenerator.nextId();
        DirectMessage toInsert = new DirectMessage(
            id,
            msg.senderId(),
            msg.receiverId(),
            msg.text(),
            msg.readAt(),
            msg.createdAt()
        );
        
        jdbcTemplate.update(
            "INSERT INTO direct_messages (id, sender_id, receiver_id, text, read_at, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            toInsert.id(), toInsert.senderId(), toInsert.receiverId(),
            toInsert.text(), toInsert.readAt(), toInsert.createdAt()
        );
        return toInsert;
    }

    public void upsertConversation(Long userId1, Long userId2, OffsetDateTime lastMessageAt) {
        Long userOneId = Math.min(userId1, userId2);
        Long userTwoId = Math.max(userId1, userId2);
        OffsetDateTime now = lastMessageAt != null ? lastMessageAt : OffsetDateTime.now();

        int updated = jdbcTemplate.update(
            "UPDATE conversations SET last_message_at = ? WHERE user_one_id = ? AND user_two_id = ?",
            now, userOneId, userTwoId
        );

        if (updated == 0) {
            Long id = idGenerator.nextId();
            jdbcTemplate.update(
                "INSERT INTO conversations (id, user_one_id, user_two_id, last_message_at, created_at) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                id, userOneId, userTwoId, now, now
            );
        }
    }

    public List<Conversation> findConversations(Long userId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM conversations WHERE user_one_id = ? OR user_two_id = ? ORDER BY last_message_at DESC",
            Conversation.class, userId, userId
        );
    }

    public List<DirectMessage> findMessages(Long userId1, Long userId2, Long cursor, int limit) {
        if (cursor == null) {
            cursor = Long.MAX_VALUE;
        }
        return jdbcTemplate.queryForList(
            "SELECT * FROM direct_messages " +
            "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
            "AND id < ? " +
            "ORDER BY id DESC LIMIT ?",
            DirectMessage.class, userId1, userId2, userId2, userId1, cursor, limit
        );
    }
    
    public void markAsRead(Long senderId, Long receiverId, OffsetDateTime readAt) {
        jdbcTemplate.update(
            "UPDATE direct_messages SET read_at = ? " +
            "WHERE sender_id = ? AND receiver_id = ? AND read_at IS NULL",
            readAt, senderId, receiverId
        );
    }
}
