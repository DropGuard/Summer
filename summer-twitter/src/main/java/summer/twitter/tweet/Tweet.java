package summer.twitter.tweet;

import summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record Tweet(
    Long id,
    Long authorId,
    String content,
    String type,
    Long parentId,
    Integer likeCount,
    Integer replyCount,
    Integer retweetCount,
    OffsetDateTime createdAt
) {}
