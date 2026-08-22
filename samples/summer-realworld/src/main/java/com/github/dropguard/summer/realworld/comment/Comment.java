package com.github.dropguard.summer.realworld.comment;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.LocalDateTime;

/** A comment on an article. */
@RowModel(table = "comments")
public record Comment(
        Long id,
        String body,
        Long articleId,
        Long authorId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
