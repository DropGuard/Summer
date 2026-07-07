package summer.twitter.social;

import summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record Follow(
    Long id,
    Long followerId,
    Long followingId,
    OffsetDateTime createdAt
) {}
