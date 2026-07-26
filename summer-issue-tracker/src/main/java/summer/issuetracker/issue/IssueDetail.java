package summer.issuetracker.issue;

import java.util.List;

import summer.issuetracker.audit.IssueHistory;
import summer.issuetracker.comment.Comment;
import summer.issuetracker.tag.Tag;

/**
 * Assembled view of an issue: the row plus its associations (tags, comments,
 * history). Summer's {@code @RowModel} cannot nest these, so the service builds
 * this aggregated DTO from separate tables. This is the payload the API returns.
 */
public record IssueDetail(
        Issue issue,
        List<Tag> tags,
        List<Comment> comments,
        List<IssueHistory> history,
        int commentCount,
        String assigneeName,
        String reporterName
) {}
