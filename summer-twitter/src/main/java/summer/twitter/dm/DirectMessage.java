package summer.twitter.dm;

import summer.data.jdbc.annotation.RowModel;
import java.time.ZonedDateTime;

@RowModel
public record DirectMessage(
    Long id,
    Long senderId,
    Long receiverId,
    String text,
    ZonedDateTime readAt,
    ZonedDateTime createdAt
) {}
