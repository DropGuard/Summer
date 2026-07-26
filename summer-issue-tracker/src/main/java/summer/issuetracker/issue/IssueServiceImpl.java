package summer.issuetracker.issue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import summer.core.Component;
import summer.issuetracker.audit.IssueHistory;
import summer.issuetracker.audit.IssueHistoryRepository;
import summer.issuetracker.comment.Comment;
import summer.issuetracker.comment.CommentRepository;
import summer.issuetracker.common.BusinessException;
import summer.issuetracker.common.IdGenerator;
import summer.issuetracker.project.Project;
import summer.issuetracker.tag.Tag;
import summer.issuetracker.project.ProjectRepository;
import summer.issuetracker.issue.Page;
import summer.issuetracker.issue.PageRequest;
import summer.issuetracker.security.ProjectAuthorization;
import summer.issuetracker.user.User;
import summer.issuetracker.user.UserRepository;
import summer.tx.Transactional;
import summer.web.RequestContextHolder;

/**
 * Concrete issue service. Implements {@link IssueService} so Summer's JDK-dynamic-
 * proxy AOP can wrap it — required for {@code @Transactional} and
 * {@code @RequireRole} to apply.
 *
 * <p>
 * RBAC is split:
 * <ul>
 *   <li><b>Coarse-grained</b> (tenant isolation, project membership, manager/lead)
 *       is enforced by {@code RbacInterceptor} before any method runs — it reads
 *       the current user from {@link RequestContextHolder}, no parameter needed.</li>
 *   <li><b>Fine-grained</b> (a plain member may only mutate issues they reported
 *       or are assigned to) is enforced here, also reading the current user from
 *       the holder. This keeps the method signatures clean.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Every mutating method writes the issue row AND the matching {@link IssueHistory}
 * inside one {@code @Transactional} boundary — proving the AOP proxy shares a
 * single connection between the two writes.
 * </p>
 */
@Component
public class IssueServiceImpl implements IssueService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IssueHistoryRepository issueHistoryRepository;
    private final CommentRepository commentRepository;
    private final IdGenerator idGenerator;
    private final ProjectAuthorization authz;

    public IssueServiceImpl(IssueRepository issueRepository, ProjectRepository projectRepository,
            UserRepository userRepository, IssueHistoryRepository issueHistoryRepository,
            CommentRepository commentRepository, IdGenerator idGenerator,
            ProjectAuthorization authz) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.issueHistoryRepository = issueHistoryRepository;
        this.commentRepository = commentRepository;
        this.idGenerator = idGenerator;
        this.authz = authz;
    }

    private long currentUserId() {
        Long id = RequestContextHolder.currentUserId();
        if (id == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        return id;
    }

    @Override
    @Transactional
    public Issue createIssue(long projectId, String title, String description,
            String status, String priority, Long assigneeId) {
        long actorId = currentUserId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> BusinessException.notFound("Project"));

        long id = idGenerator.nextId();
        String key = project.projectKey() + "-" + projectRepository.nextIssueSeq(projectId);
        OffsetDateTime now = OffsetDateTime.now();
        Issue issue = new Issue(id, projectId, key, title, description,
                normalizeStatus(status), normalizePriority(priority), assigneeId, actorId, now, now);
        issueRepository.insert(issue);
        issueHistoryRepository.insert(new IssueHistory(idGenerator.nextId(), id, actorId, "CREATED",
                null, null, now));
        return issue;
    }

    @Override
    @Transactional
    public Issue updateStatus(long issueId, String newStatus) {
        long actorId = currentUserId();
        Issue issue = load(issueId);
        Project project = projectRepository.findById(issue.projectId()).orElseThrow();
        assertOwns(actorId, issue, project, "change status");
        String normalized = normalizeStatus(newStatus);
        if (normalized.equals(issue.status())) {
            return issue;
        }
        Issue updated = new Issue(issue.id(), issue.projectId(), issue.issueKey(), issue.title(),
                issue.description(), normalized, issue.priority(), issue.assigneeId(),
                issue.reporterId(), issue.createdAt(), OffsetDateTime.now());
        issueRepository.updateMutable(updated);
        issueHistoryRepository.insert(new IssueHistory(idGenerator.nextId(), issueId, actorId,
                "STATUS_CHANGED", issue.status(), normalized, OffsetDateTime.now()));
        return updated;
    }

    @Override
    @Transactional
    public Issue assign(long issueId, Long newAssigneeId) {
        long actorId = currentUserId();
        Issue issue = load(issueId);
        Project project = projectRepository.findById(issue.projectId()).orElseThrow();
        assertOwns(actorId, issue, project, "reassign");
        if (newAssigneeId != null && userRepository.findById(newAssigneeId).isEmpty()) {
            throw BusinessException.notFound("Assignee user");
        }
        if (newAssigneeId != null && !authz.isMember(newAssigneeId, project.id())) {
            throw BusinessException.badRequest("Assignee is not a member of this project");
        }
        Issue updated = new Issue(issue.id(), issue.projectId(), issue.issueKey(), issue.title(),
                issue.description(), issue.status(), issue.priority(), newAssigneeId,
                issue.reporterId(), issue.createdAt(), OffsetDateTime.now());
        issueRepository.updateMutable(updated);
        String from = issue.assigneeId() == null ? "none" : String.valueOf(issue.assigneeId());
        String to = newAssigneeId == null ? "none" : String.valueOf(newAssigneeId);
        issueHistoryRepository.insert(new IssueHistory(idGenerator.nextId(), issueId, actorId,
                "ASSIGNEE_CHANGED", from, to, OffsetDateTime.now()));
        return updated;
    }

    @Override
    @Transactional
    public Issue changePriority(long issueId, String newPriority) {
        long actorId = currentUserId();
        Issue issue = load(issueId);
        Project project = projectRepository.findById(issue.projectId()).orElseThrow();
        assertOwns(actorId, issue, project, "change priority");
        String normalized = normalizePriority(newPriority);
        if (normalized.equals(issue.priority())) {
            return issue;
        }
        Issue updated = new Issue(issue.id(), issue.projectId(), issue.issueKey(), issue.title(),
                issue.description(), issue.status(), normalized, issue.assigneeId(),
                issue.reporterId(), issue.createdAt(), OffsetDateTime.now());
        issueRepository.updateMutable(updated);
        issueHistoryRepository.insert(new IssueHistory(idGenerator.nextId(), issueId, actorId,
                "PRIORITY_CHANGED", issue.priority(), normalized, OffsetDateTime.now()));
        return updated;
    }

    @Override
    @Transactional
    public Issue updateIssue(long issueId, String title, String description) {
        long actorId = currentUserId();
        Issue issue = load(issueId);
        Project project = projectRepository.findById(issue.projectId()).orElseThrow();
        assertOwns(actorId, issue, project, "edit");
        if (title == null || title.isBlank()) {
            throw BusinessException.badRequest("Title must not be empty");
        }
        Issue updated = new Issue(issue.id(), issue.projectId(), issue.issueKey(), title,
                description, issue.status(), issue.priority(), issue.assigneeId(),
                issue.reporterId(), issue.createdAt(), OffsetDateTime.now());
        issueRepository.updateMutable(updated);
        issueHistoryRepository.insert(new IssueHistory(idGenerator.nextId(), issueId, actorId,
                "EDITED", null, null, OffsetDateTime.now()));
        return updated;
    }

    @Override
    @Transactional
    public void deleteIssue(long issueId) {
        long actorId = currentUserId();
        Issue issue = load(issueId);
        // Record the deletion while the row still exists (FK satisfied), then remove
        // the issue — the issue_history row is cascade-deleted with it, keeping the
        // history is scoped to the issue (child resource, cascade-deleted)'s lifetime.
        issueHistoryRepository.insert(new IssueHistory(idGenerator.nextId(), issueId, actorId,
                "DELETED", issue.issueKey(), null, OffsetDateTime.now()));
        issueRepository.delete(issueId);
    }

    @Override
    public Optional<Issue> getIssue(long issueId) {
        return issueRepository.findById(issueId);
    }

    @Override
    public IssueDetail getIssueDetail(long issueId) {
        Issue issue = load(issueId);
        List<Tag> tags = issueRepository.findTags(issueId);
        List<Comment> comments = issueRepository.findComments(issueId);
        List<IssueHistory> history = issueHistoryRepository.findByIssue(issueId);
        return new IssueDetail(issue, tags, comments, history, comments.size(),
                nameOf(issue.assigneeId()), nameOf(issue.reporterId()));
    }

    @Override
    public IssueDetail getIssueDetailByKey(long projectId, String issueKey) {
        Issue issue = issueRepository.findByKey(projectId, issueKey)
                .orElseThrow(() -> BusinessException.notFound("Issue"));
        return getIssueDetail(issue.id());
    }

    @Override
    public List<Tag> getTags(long issueId) {
        return issueRepository.findTags(issueId);
    }

    @Override
    public List<IssueHistory> getHistory(long issueId) {
        return issueHistoryRepository.findByIssue(issueId);
    }

    @Override
    public List<Issue> search(long projectId, IssueFilter filter) {
        return issueRepository.search(projectId, filter);
    }

    @Override
    public Page<Issue> searchPage(long projectId, IssueFilter filter, PageRequest page) {
        return issueRepository.searchPage(projectId, filter, page.offset(), page.size());
    }

    @Override
    @Transactional
    public Comment addComment(long issueId, String body) {
        long actorId = currentUserId();
        Issue issue = load(issueId);
        Comment comment = new Comment(idGenerator.nextId(), issueId, actorId, body, OffsetDateTime.now());
        commentRepository.insert(comment);
        return comment;
    }

    @Override
    public List<Comment> getComments(long issueId) {
        return issueRepository.findComments(issueId);
    }

    // ── Fine-grained RBAC (coarse-grained lives in RbacInterceptor) ──────

    /**
     * A plain member may only mutate issues they reported or are assigned to;
     * managers and the lead are unrestricted (the interceptor already confirmed
     * membership / lead status for everyone). Delegated to {@link ProjectAuthorization}
     * so the rule is defined in one place.
     */
    private void assertOwns(long actorId, Issue issue, Project project, String action) {
        authz.assertOwns(actorId, issue, project, action);
    }

    private Issue load(long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> BusinessException.notFound("Issue"));
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::displayName).orElse("unknown");
    }

    private static String normalizeStatus(String s) {
        if (s == null) {
            return "OPEN";
        }
        return switch (s.toUpperCase()) {
            case "OPEN", "IN_PROGRESS", "BLOCKED", "DONE", "CLOSED" -> s.toUpperCase();
            default -> throw BusinessException.badRequest("Invalid status: " + s);
        };
    }

    private static String normalizePriority(String p) {
        if (p == null) {
            return "MEDIUM";
        }
        return switch (p.toUpperCase()) {
            case "LOW", "MEDIUM", "HIGH", "CRITICAL" -> p.toUpperCase();
            default -> throw BusinessException.badRequest("Invalid priority: " + p);
        };
    }
}
