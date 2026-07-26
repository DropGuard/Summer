package summer.issuetracker.issue;

import summer.data.jdbc.annotation.RowModel;

/** Join table for the issue <-> tag many-to-many relationship. */
@RowModel(table = "issue_tags")
public record IssueTag(
        Long issueId,
        Long tagId
) {}
