package com.github.dropguard.summer.issuetracker.tag;

import java.util.List;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.common.IdGenerator;
import com.github.dropguard.summer.issuetracker.issue.Issue;
import com.github.dropguard.summer.issuetracker.issue.IssueRepository;
import com.github.dropguard.summer.issuetracker.issue.IssueService;
import com.github.dropguard.summer.issuetracker.project.Project;
import com.github.dropguard.summer.issuetracker.project.ProjectRepository;
import com.github.dropguard.summer.issuetracker.security.ProjectAuthorization;
import com.github.dropguard.summer.issuetracker.security.SecurityContext;
import com.github.dropguard.summer.issuetracker.user.User;
import com.github.dropguard.summer.issuetracker.user.UserRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController
@Component
public class TagController {

    private final TagRepository tagRepository;
    private final IdGenerator idGenerator;
    private final IssueService issueService;
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorization authz;

    public TagController(TagRepository tagRepository, IdGenerator idGenerator, IssueService issueService,
            IssueRepository issueRepository, ProjectRepository projectRepository,
            UserRepository userRepository, ProjectAuthorization authz) {
        this.tagRepository = tagRepository;
        this.idGenerator = idGenerator;
        this.issueService = issueService;
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.authz = authz;
    }

    public record CreateTagRequest(
            @jakarta.validation.constraints.NotBlank String name,
            @jakarta.validation.constraints.NotBlank String color) {}

    @Post("/api/orgs/:orgId/tags")
    public void create(HttpContext ctx, @PathParam("orgId") Long orgId) {
        User actor = currentActor();
        if (!actor.orgId().equals(orgId)) {
            throw BusinessException.forbidden("You can only manage tags in your own organization");
        }
        CreateTagRequest req = ctx.validatedBody(CreateTagRequest.class);
        if (tagRepository.findByName(orgId, req.name()).isPresent()) {
            throw BusinessException.badRequest("Tag already exists in this org");
        }
        Tag tag = new Tag(idGenerator.nextId(), orgId, req.name(), req.color());
        tagRepository.insert(tag);
        ctx.json(HttpStatus.CREATED, tag);
    }

    @Get("/api/orgs/:orgId/tags")
    public void list(HttpContext ctx, @PathParam("orgId") Long orgId) {
        User actor = currentActor();
        if (!actor.orgId().equals(orgId)) {
            throw BusinessException.forbidden("You can only view tags in your own organization");
        }
        ctx.ok(tagRepository.findByOrg(orgId));
    }

    @Get("/api/issues/:id/tags")
    public void issueTags(HttpContext ctx, @PathParam("id") Long id) {
        // Routed through IssueService, which enforces issue-scoped RBAC via @RequireRole.
        ctx.ok(issueService.getTags(id));
    }

    @Post("/api/issues/:id/tags/:tagId")
    public void attach(HttpContext ctx, @PathParam("id") Long id, @PathParam("tagId") Long tagId) {
        // Cross-org guard: the issue, its project, and the tag must all belong to the
        // actor's organization, and the actor must be able to access the project.
        // This endpoint hits the repository directly, so the check is explicit.
        User actor = currentActor();
        Issue issue = issueRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Issue"));
        Project project = projectRepository.findById(issue.projectId()).orElseThrow();
        authz.assertSameOrg(actor, project);
        authz.assertCanAccess(actor, project);
        Tag tag = tagRepository.findById(tagId).orElseThrow(() -> BusinessException.notFound("Tag"));
        if (!tag.orgId().equals(actor.orgId())) {
            throw BusinessException.forbidden("Tag does not belong to your organization");
        }
        tagRepository.attach(id, tagId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @Delete("/api/issues/:id/tags/:tagId")
    public void detach(HttpContext ctx, @PathParam("id") Long id, @PathParam("tagId") Long tagId) {
        User actor = currentActor();
        Issue issue = issueRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Issue"));
        Project project = projectRepository.findById(issue.projectId()).orElseThrow();
        authz.assertSameOrg(actor, project);
        authz.assertCanAccess(actor, project);
        Tag tag = tagRepository.findById(tagId).orElseThrow(() -> BusinessException.notFound("Tag"));
        if (!tag.orgId().equals(actor.orgId())) {
            throw BusinessException.forbidden("Tag does not belong to your organization");
        }
        tagRepository.detach(id, tagId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private User currentActor() {
        long actorId = SecurityContext.currentUserId();
        return userRepository.findById(actorId)
                .orElseThrow(() -> BusinessException.unauthorized("Unknown actor"));
    }
}
