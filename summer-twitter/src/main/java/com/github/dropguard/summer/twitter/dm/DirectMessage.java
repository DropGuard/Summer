package com.github.dropguard.summer.twitter.dm;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel(table = "direct_messages")
public record DirectMessage(
    Long id,
    Long senderId,
    Long receiverId,
    String text,
    OffsetDateTime readAt,
    OffsetDateTime createdAt
) {}
