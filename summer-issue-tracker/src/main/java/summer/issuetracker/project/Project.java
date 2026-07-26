package summer.issuetracker.project;

import summer.data.jdbc.annotation.RowModel;

import java.time.OffsetDateTime;

@RowModel(table = "projects")
public record Project(
        Long id,
        Long orgId,
        String projectKey,
        String name,
        Long leadUserId,
        OffsetDateTime createdAt
) {}
