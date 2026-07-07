package summer.twitter.dm;

import summer.data.jdbc.annotation.RowModel;
import java.time.ZonedDateTime;

@RowModel
public record Conversation(
    Long id,
    Long userOneId,
    Long userTwoId,
    ZonedDateTime lastMessageAt,
    ZonedDateTime createdAt
) {}
