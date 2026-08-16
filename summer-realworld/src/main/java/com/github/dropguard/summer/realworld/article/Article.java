package com.github.dropguard.summer.realworld.article;

import com.github.dropguard.summer.data.jdbc.annotation.RowModel;
import java.time.LocalDateTime;

/**
 * An article row. {@code tagList} and {@code favoritesCount} are assembled at the API layer (from
 * {@code article_tags} and the {@code favorites} table), not stored on the row — {@code @RowModel}
 * rejects {@code List} fields, and the favorite count is computed, so this record carries only the
 * physical columns.
 */
@RowModel(table = "articles")
public record Article(
        Long id,
        String slug,
        String title,
        String description,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long authorId) {}
