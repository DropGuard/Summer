package com.github.dropguard.summer.issuetracker.security;

import java.util.Objects;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.issue.Issue;
import com.github.dropguard.summer.issuetracker.issue.IssueRepository;
import com.github.dropguard.summer.issuetracker.project.Project;
import com.github.dropguard.summer.issuetracker.project.ProjectMember;
import com.github.dropguard.summer.issuetracker.project.ProjectRepository;
import com.github.dropguard.summer.issuetracker.user.User;
import com.github.dropguard.summer.issuetracker.user.UserRepository;

/**
 * Single source of truth for project-scoped authorization. Both
 * {@link RbacInterceptor} (coarse-grained, request-aware) and
 * {@link com.github.dropguard.summer.issuetracker.issue.IssueServiceImpl} (fine-grained ownership)
 * delegate here so the membership / manager / lead rules live in exactly one
 * place.
 */
@Component
public class ProjectAuthorization {

    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    public ProjectAuthorization(ProjectRepository projectRepository, IssueRepository issueRepository,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
    }

    public User requireActor(long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> BusinessException.unauthorized("Unknown actor"));
    }

    /** The project a resource id belongs to: an issue id resolves to its project. */
    public Project resolveProject(ResourceScope.Kind kind, long resourceId) {
        if (kind == ResourceScope.Kind.PROJECT) {
            return projectRepository.findById(resourceId)
                    .orElseThrow(() -> BusinessException.notFound("Project"));
        }
        Issue issue = issueRepository.findById(resourceId)
                .orElseThrow(() -> BusinessException.notFound("Issue"));
        return projectRepository.findById(issue.projectId())
                .orElseThrow(() -> BusinessException.notFound("Project"));
    }

    /** Tenant isolation: an actor may only touch resources in their own org. */
    public void assertSameOrg(User actor, Project project) {
        if (!actor.orgId().equals(project.orgId())) {
            throw BusinessException.forbidden("You do not belong to this organization");
        }
    }

    /**
     * Coarse-grained gate used by the interceptor. ADMIN is all-powerful only
     * within their own organization (tenant isolation is checked separately);
     * MANAGER and the project lead may manage; plain members may only read.
     */
    public void assertCanAccess(User actor, Project project) {
        Role orgRole = Role.valueOf(actor.role());
        if (orgRole.hasAtLeast(Role.ADMIN)) {
            return;
        }
        if (!isMember(actor.id(), project.id())) {
            throw BusinessException.forbidden("You are not a member of this project");
        }
    }

    /** Only a manager or the project lead may perform destructive admin actions. */
    public void assertCanAdminister(User actor, Project project) {
        if (!isManagerOrLead(actor, project)) {
            throw BusinessException.forbidden("Only a project manager or lead can perform this action");
        }
    }

    public boolean isManagerOrLead(long actorId, Project project) {
        if (project.leadUserId() == actorId) {
            return true;
        }
        return projectRepository.findMember(project.id(), actorId)
                .map(ProjectMember::role)
                .map("MANAGER"::equals)
                .orElse(false);
    }

    public boolean isManagerOrLead(User actor, Project project) {
        return isManagerOrLead(actor.id(), project);
    }

    public boolean isMember(long userId, long projectId) {
        return projectRepository.findMember(projectId, userId).isPresent();
    }

    /** Fine-grained ownership: a plain member may only mutate issues they own. */
    public void assertOwns(long actorId, Issue issue, Project project, String action) {
        if (isManagerOrLead(actorId, project)) {
            return;
        }
        // assigneeId is nullable (Long), so comparisons must be null-safe: a raw
        // `actorId == issue.assigneeId()` would autounbox the null Long and throw
        // NPE on an unassigned issue. Objects.equals also coerces the primitive
        // actorId to Long, so the comparison is always box-safe.
        boolean owns = Objects.equals(actorId, issue.assigneeId())
                || Objects.equals(actorId, issue.reporterId());
        if (!owns) {
            throw BusinessException.forbidden(
                    "You can only " + action + " on issues assigned to or reported by you");
        }
    }
}
