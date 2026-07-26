package summer.issuetracker.issue;

import summer.data.jdbc.annotation.RowModel;

import java.time.OffsetDateTime;

/**
 * Core issue/defect entity. All columns are JDBC-native scalars — the framework's
 * {@code @RowModel} rejects nested records or collection fields, so the many-to-many
 * tag relationship and the nested comment stream are modelled as separate tables
 * and assembled in the service layer, not as fields here.
 */
@RowModel(table = "issues")
public record Issue(
        Long id,
        Long projectId,
        String issueKey,
        String title,
        String description,
        String status,
        String priority,
        Long assigneeId,
        Long reporterId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
