package summer.issuetracker.org;

import summer.data.jdbc.annotation.RowModel;

import java.time.OffsetDateTime;

@RowModel(table = "organizations")
public record Organization(
        Long id,
        String name,
        String slug,
        OffsetDateTime createdAt
) {}
