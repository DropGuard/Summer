package com.github.dropguard.summer.twitter.social;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel
public record Follow(
    Long id,
    Long followerId,
    Long followingId,
    OffsetDateTime createdAt
) {}
