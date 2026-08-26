package com.github.dropguard.summer.issuetracker.issue;

import com.github.dropguard.summer.issuetracker.audit.IssueHistory;
import com.github.dropguard.summer.issuetracker.comment.Comment;
import com.github.dropguard.summer.issuetracker.tag.Tag;
import java.util.List;

/**
 * Assembled view of an issue: the row plus its associations (tags, comments, history). Summer's
 * {@code @RowModel} cannot nest these, so the service builds this aggregated DTO from separate
 * tables. This is the payload the API returns.
 */
public record IssueDetail(
        Issue issue,
        List<Tag> tags,
        List<Comment> comments,
        List<IssueHistory> history,
        Integer commentCount,
        String assigneeName,
        String reporterName) {}
