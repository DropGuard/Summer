package summer.twitter.dm;

import summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record Conversation(
    Long id,
    Long userOneId,
    Long userTwoId,
    OffsetDateTime lastMessageAt,
    OffsetDateTime createdAt
) {}
