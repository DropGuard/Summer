package com.github.dropguard.summer.issuetracker.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dropguard.summer.issuetracker.audit.IssueHistoryRepository;
import com.github.dropguard.summer.issuetracker.comment.CommentRepository;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.common.IdGenerator;
import com.github.dropguard.summer.issuetracker.project.Project;
import com.github.dropguard.summer.issuetracker.project.ProjectRepository;
import com.github.dropguard.summer.issuetracker.security.ProjectAuthorization;
import com.github.dropguard.summer.issuetracker.user.User;
import com.github.dropguard.summer.issuetracker.user.UserRepository;
import com.github.dropguard.summer.web.RequestContextHolder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Behavioral unit test for {@link IssueServiceImpl} — the demo's OWN logic, not Summer's.
 * Repositories are mocked; the class is instantiated directly, so this test never touches the
 * framework container or its AOP proxy. (The request-scoped user comes from {@link
 * RequestContextHolder}, set here directly; the coarse-grained RBAC gate lives in {@code
 * RbacInterceptor}, exercised by the IT. Transactional commit/rollback parity is covered by the IT
 * too.)
 *
 * <p>{@link ProjectAuthorization} is mocked and stubbed so fine-grained ownership is exercised
 * without wiring the real rules: it throws for a forbidden actor and is a no-op for an allowed one.
 */
class IssueServiceImplTest {

    @Mock private IssueRepository issueRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private IssueHistoryRepository issueHistoryRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private IdGenerator idGenerator;
    @Mock private ProjectAuthorization authz;

    private IssueServiceImpl service;

    private static final long PROJECT_ID = 10L;
    private static final long ORG_ID = 5L;
    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long ISSUE_ID = 100L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service =
                new IssueServiceImpl(
                        issueRepository,
                        projectRepository,
                        userRepository,
                        issueHistoryRepository,
                        commentRepository,
                        idGenerator,
                        authz);
        when(idGenerator.nextId()).thenReturn(999L);
        RequestContextHolder.set(OWNER_ID);
        // Fine-grained ownership passes by default; individual tests tighten it.
        org.mockito.Mockito.doNothing().when(authz).assertOwns(eq(OWNER_ID), any(), any(), any());
    }

    @Test
    void createWritesIssueAndAudit() {
        Project project = new Project(PROJECT_ID, ORG_ID, "DEMO", "Demo", OWNER_ID, null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.nextIssueSeq(PROJECT_ID)).thenReturn(1L);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "MEMBER")));

        Issue result = service.createIssue(PROJECT_ID, "Title", "Desc", "OPEN", "HIGH", null);

        assertEquals("DEMO-1", result.issueKey());
        verify(issueRepository).insert(any(Issue.class));
        verify(issueHistoryRepository).insert(any()); // CREATED audit row
    }

    @Test
    void issueKeyIncrementsWithProjectCount() {
        Project project = new Project(PROJECT_ID, ORG_ID, "DEMO", "Demo", OWNER_ID, null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.nextIssueSeq(PROJECT_ID)).thenReturn(4L);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "MEMBER")));

        Issue result = service.createIssue(PROJECT_ID, "Title", "Desc", "OPEN", "HIGH", null);
        assertEquals("DEMO-4", result.issueKey());
    }

    @Test
    void invalidPriorityRejected() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "MEMBER")));
        Project project = new Project(PROJECT_ID, ORG_ID, "DEMO", "Demo", OWNER_ID, null);
        Issue issue =
                new Issue(
                        ISSUE_ID,
                        PROJECT_ID,
                        "DEMO-1",
                        "T",
                        null,
                        "OPEN",
                        "LOW",
                        null,
                        OWNER_ID,
                        null,
                        null);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThrows(BusinessException.class, () -> service.changePriority(ISSUE_ID, "URGENT"));
    }

    @Test
    void nullStatusOrPriorityRejected() {
        Project project = new Project(PROJECT_ID, ORG_ID, "DEMO", "Demo", OWNER_ID, null);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.nextIssueSeq(PROJECT_ID)).thenReturn(1L);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(user(OWNER_ID, "MEMBER")));

        assertThrows(
                BusinessException.class,
                () -> service.createIssue(PROJECT_ID, "Title", "Desc", null, "MEDIUM", null),
                "null status should be rejected");
        assertThrows(
                BusinessException.class,
                () -> service.createIssue(PROJECT_ID, "Title", "Desc", "OPEN", null, null),
                "null priority should be rejected");
    }

    @Test
    void updateIssueEditsTitleAndRecordsAudit() {
        Project project = new Project(PROJECT_ID, ORG_ID, "DEMO", "Demo", OWNER_ID, null);
        Issue issue =
                new Issue(
                        ISSUE_ID,
                        PROJECT_ID,
                        "DEMO-1",
                        "Old",
                        "Old body",
                        "OPEN",
                        "LOW",
                        null,
                        OWNER_ID,
                        null,
                        null);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        Issue updated = service.updateIssue(ISSUE_ID, "New", "New body");
        assertEquals("New", updated.title());
        assertEquals("New body", updated.description());
        verify(issueRepository).updateMutable(any());
        verify(issueHistoryRepository).insert(any()); // EDITED audit row
    }

    @Test
    void updateIssueForbiddenForNonOwner() {
        Project project = new Project(PROJECT_ID, ORG_ID, "DEMO", "Demo", OWNER_ID, null);
        Issue issue =
                new Issue(
                        ISSUE_ID,
                        PROJECT_ID,
                        "DEMO-1",
                        "T",
                        null,
                        "OPEN",
                        "LOW",
                        null,
                        OWNER_ID,
                        null,
                        null);
        when(issueRepository.findById(ISSUE_ID)).thenReturn(Optional.of(issue));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        // The current user is OWNER_ID; make the issue owned by someone else.
        org.mockito.Mockito.doThrow(BusinessException.forbidden("no"))
                .when(authz)
                .assertOwns(eq(OTHER_ID), any(), any(), any());
        RequestContextHolder.set(OTHER_ID);

        assertThrows(
                BusinessException.class, () -> service.updateIssue(ISSUE_ID, "New", "New body"));
        verify(issueRepository, never()).updateMutable(any());
    }

    private static User user(long id, String role) {
        return new User(id, ORG_ID, "u" + id, "U" + id, "u" + id + "@x.com", "h", role, null);
    }
}
