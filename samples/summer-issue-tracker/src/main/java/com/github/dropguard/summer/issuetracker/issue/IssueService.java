package com.github.dropguard.summer.issuetracker.issue;
import com.github.dropguard.summer.core.data.Page;
import com.github.dropguard.summer.core.data.PageRequest;

import com.github.dropguard.summer.issuetracker.audit.IssueHistory;
import com.github.dropguard.summer.issuetracker.comment.Comment;
import com.github.dropguard.summer.issuetracker.tag.Tag;
import java.util.List;
import java.util.Optional;

/**
 * Issue domain service contract.
 *
 * <p>Deliberately an <b>interface</b> with a separate impl — Summer's AOP uses JDK dynamic proxies,
 * which require the proxied bean to implement an interface, so {@code @Transactional} can be
 * applied.
 *
 * <p>The <em>actor</em> (current user) is an explicit first parameter on every mutating method. It
 * is threaded from the HTTP layer (the handler reads the {@code userId} request attribute set by
 * the auth middleware), not resolved from ambient state — the Gin-style contract. Authorization is
 * split: the coarse gate (tenant isolation, project membership, manager-or-lead) is enforced by
 * {@code RbacMiddleware} at the HTTP layer before any handler runs; the fine-grained rule (a plain
 * member may only mutate issues they reported or are assigned to) and the system audit trail live
 * here, where the resource is at hand.
 */
public interface IssueService {

    Issue createIssue(
            long actorId,
            long projectId,
            String title,
            String description,
            String status,
            String priority,
            Long assigneeId);

    Issue updateStatus(long actorId, long issueId, String newStatus);

    Issue assign(long actorId, long issueId, Long newAssigneeId);

    Issue changePriority(long actorId, long issueId, String newPriority);

    Issue updateIssue(long actorId, long issueId, String title, String description);

    void deleteIssue(long actorId, long issueId);

    Optional<Issue> getIssue(long issueId);

    /** Full detail: issue + tags + comments + history (assembled in the service layer). */
    IssueDetail getIssueDetail(long issueId);

    /** Resolve an issue by its business key (PROJECT-NN) within a project. */
    IssueDetail getIssueDetailByKey(long projectId, String issueKey);

    List<Tag> getTags(long issueId);

    List<IssueHistory> getHistory(long issueId);

    /** Dynamic board filter — exercises Summer's QueryBuilder for single-entity criteria. */
    List<Issue> search(long projectId, IssueFilter filter);

    /** Paginated board filter; returns the page plus total match count. */
    Page<Issue> searchPage(long projectId, IssueFilter filter, PageRequest page);

    Comment addComment(long actorId, long issueId, String body);

    List<Comment> getComments(long issueId);
}
