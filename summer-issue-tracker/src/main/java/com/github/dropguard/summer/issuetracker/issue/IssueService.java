package com.github.dropguard.summer.issuetracker.issue;

import java.util.List;
import java.util.Optional;

import com.github.dropguard.summer.issuetracker.audit.IssueHistory;
import com.github.dropguard.summer.issuetracker.comment.Comment;
import com.github.dropguard.summer.issuetracker.security.RequireRole;
import com.github.dropguard.summer.issuetracker.security.ResourceScope;
import com.github.dropguard.summer.issuetracker.tag.Tag;

/**
 * Issue domain service contract.
 *
 * <p>
 * Deliberately an <b>interface</b> with a separate impl — Summer's AOP uses JDK
 * dynamic proxies, which require the proxied bean to implement an interface, so
 * {@code @Transactional} and {@code @RequireRole} can be applied.
 * </p>
 *
 * <p>
 * Note what is <em>absent</em>: there is no {@code actorId} parameter on any
 * method. The current user is resolved inside the service (for fine-grained
 * ownership checks and the audit trail's actor) and in {@link
 * com.github.dropguard.summer.issuetracker.security.RbacInterceptor} (for the coarse-grained gate)
 * from {@link com.github.dropguard.summer.web.RequestContextHolder} — published by the framework's
 * auth middleware. This is the corrected design: the Gin-style "thread the user
 * id through every call" was replaced by a framework-level request context.
 * </p>
 */
@RequireRole
public interface IssueService {

    @ResourceScope(ResourceScope.Kind.PROJECT)
    Issue createIssue(long projectId, String title, String description,
            String status, String priority, Long assigneeId);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    Issue updateStatus(long issueId, String newStatus);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    Issue assign(long issueId, Long newAssigneeId);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    Issue changePriority(long issueId, String newPriority);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    Issue updateIssue(long issueId, String title, String description);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    void deleteIssue(long issueId);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    Optional<Issue> getIssue(long issueId);

    /** Full detail: issue + tags + comments + history (assembled in the service layer). */
    @ResourceScope(ResourceScope.Kind.ISSUE)
    IssueDetail getIssueDetail(long issueId);

    /** Resolve an issue by its business key (PROJECT-NN) within a project. */
    @ResourceScope(ResourceScope.Kind.PROJECT)
    IssueDetail getIssueDetailByKey(long projectId, String issueKey);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    List<Tag> getTags(long issueId);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    List<IssueHistory> getHistory(long issueId);

    /** Dynamic board filter — exercises Summer's QueryBuilder for single-entity criteria. */
    @ResourceScope(ResourceScope.Kind.PROJECT)
    List<Issue> search(long projectId, IssueFilter filter);

    /** Paginated board filter; returns the page plus total match count. */
    @ResourceScope(ResourceScope.Kind.PROJECT)
    Page<Issue> searchPage(long projectId, IssueFilter filter, PageRequest page);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    Comment addComment(long issueId, String body);

    @ResourceScope(ResourceScope.Kind.ISSUE)
    List<Comment> getComments(long issueId);
}
