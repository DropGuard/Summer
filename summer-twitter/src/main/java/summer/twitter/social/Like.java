package summer.twitter.social;

import summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record Like(
    Long id,
    Long userId,
    Long tweetId,
    OffsetDateTime createdAt
) {}
