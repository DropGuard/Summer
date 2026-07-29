package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel(table = "likes")
public record Like(
    Long id,
    Long userId,
    Long tweetId,
    OffsetDateTime createdAt
) {}
