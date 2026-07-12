package summer.twitter.dm;

import summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record DirectMessage(
    Long id,
    Long senderId,
    Long receiverId,
    String text,
    OffsetDateTime readAt,
    OffsetDateTime createdAt
) {}
