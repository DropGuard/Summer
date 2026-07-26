package summer.issuetracker.comment;

import summer.data.jdbc.annotation.RowModel;

import java.time.OffsetDateTime;

@RowModel(table = "comments")
public record Comment(
        Long id,
        Long issueId,
        Long authorId,
        String body,
        OffsetDateTime createdAt
) {}
