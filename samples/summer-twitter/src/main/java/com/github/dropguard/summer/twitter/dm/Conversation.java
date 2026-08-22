package com.github.dropguard.summer.twitter.dm;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.OffsetDateTime;

@RowModel(table = "conversations")
public record Conversation(
        Long id,
        Long userOneId,
        Long userTwoId,
        OffsetDateTime lastMessageAt,
        OffsetDateTime createdAt) {}
