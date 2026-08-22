package com.github.dropguard.summer.twitter.tweet;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel(table = "tweets")
public record Tweet(
        Long id,
        Long authorId,
        String content,
        String type,
        Long parentId,
        Integer likeCount,
        Integer replyCount,
        Integer retweetCount,
        OffsetDateTime createdAt) {}
