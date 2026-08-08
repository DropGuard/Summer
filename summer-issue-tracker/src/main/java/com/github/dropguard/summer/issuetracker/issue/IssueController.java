package com.github.dropguard.summer.issuetracker.issue;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.project.ProjectRepository;
import com.github.dropguard.summer.issuetracker.security.SecurityContext;
import com.github.dropguard.summer.issuetracker.user.User;
import com.github.dropguard.summer.issuetracker.user.UserRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.QueryParam;
import com.github.dropguard.summer.web.annotation.RestController;

@RestController
@Component
public class IssueController {

    private final IssueService issueService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public IssueController(
            IssueService issueService,
            ProjectRepository projectRepository,
            UserRepository userRepository) {
        this.issueService = issueService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public record CreateIssueRequest(
            @jakarta.validation.constraints.NotBlank String title,
            String description,
            @jakarta.validation.constraints.NotBlank String status,
            @jakarta.validation.constraints.NotBlank String priority,
            Long assigneeId) {}

    public record UpdateStatusRequest(@jakarta.validation.constraints.NotBlank String status) {}

    public record UpdateIssueRequest(
            @jakarta.validation.constraints.NotBlank String title, String description) {}

    public record AssignRequest(Long assigneeId) {}

    public record PriorityRequest(@jakarta.validation.constraints.NotBlank String priority) {}

    public record CommentRequest(@jakarta.validation.constraints.NotBlank String body) {}

    @Post("/api/projects/:id/issues")
    public void create(HttpContext ctx, @PathParam("id") Long projectId) {
        CreateIssueRequest req = ctx.body(CreateIssueRequest.class);
        var issue =
                issueService.createIssue(
                        projectId,
                        req.title(),
                        req.description(),
                        req.status(),
                        req.priority(),
                        req.assigneeId());
        ctx.json(HttpStatus.CREATED, issue);
    }

    @Get("/api/projects/:id/issues")
    public void list(
            HttpContext ctx,
            @PathParam("id") Long projectId,
            @QueryParam("assigneeId") Long assigneeId,
            @QueryParam("status") String status,
            @QueryParam("priority") String priority,
            @QueryParam("reporterId") Long reporterId,
            @QueryParam("title") String title,
            @QueryParam("tagId") Long tagId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size) {
        IssueFilter filter =
                new IssueFilter.Builder()
                        .assigneeId(assigneeId)
                        .status(status)
                        .priority(priority)
                        .reporterId(reporterId)
                        .titleContains(title)
                        .tagId(tagId)
                        .build();
        PageRequest req =
                new PageRequest(
                        page == null ? 0 : page, size == null ? PageRequest.DEFAULT_SIZE : size);
        ctx.ok(issueService.searchPage(projectId, filter, req));
    }

    @Get("/api/issues/:id")
    public void detail(HttpContext ctx, @PathParam("id") Long id) {
        ctx.ok(issueService.getIssueDetail(id));
    }

    @Get("/api/issues/key/:key")
    public void byKey(HttpContext ctx, @PathParam("key") String key) {
        // Resolve the owning project from the issue key prefix (PROJECTKEY-NN),
        // scoped to the current user's org. A key from another org resolves to
        // notFound here — never leaks that the project exists.
        int dash = key.lastIndexOf('-');
        if (dash <= 0) {
            throw com.github.dropguard.summer.issuetracker.common.BusinessException.badRequest(
                    "Invalid issue key");
        }
        String projectKey = key.substring(0, dash);
        long actorId = SecurityContext.currentUserId();
        User actor =
                userRepository
                        .findById(actorId)
                        .orElseThrow(
                                () ->
                                        com.github.dropguard.summer.issuetracker.common
                                                .BusinessException.unauthorized("Unknown actor"));
        var project =
                projectRepository
                        .findByKey(actor.orgId(), projectKey)
                        .orElseThrow(
                                () ->
                                        com.github.dropguard.summer.issuetracker.common
                                                .BusinessException.notFound("Project"));
        var issue = issueService.getIssueDetailByKey(project.id(), key);
        ctx.ok(issue);
    }

    @Put("/api/issues/:id")
    public void update(HttpContext ctx, @PathParam("id") Long id) {
        UpdateIssueRequest req = ctx.body(UpdateIssueRequest.class);
        ctx.ok(issueService.updateIssue(id, req.title(), req.description()));
    }

    @Put("/api/issues/:id/status")
    public void updateStatus(HttpContext ctx, @PathParam("id") Long id) {
        UpdateStatusRequest req = ctx.body(UpdateStatusRequest.class);
        ctx.ok(issueService.updateStatus(id, req.status()));
    }

    @Put("/api/issues/:id/assign")
    public void assign(HttpContext ctx, @PathParam("id") Long id) {
        AssignRequest req = ctx.body(AssignRequest.class);
        ctx.ok(issueService.assign(id, req.assigneeId()));
    }

    @Put("/api/issues/:id/priority")
    public void changePriority(HttpContext ctx, @PathParam("id") Long id) {
        PriorityRequest req = ctx.body(PriorityRequest.class);
        ctx.ok(issueService.changePriority(id, req.priority()));
    }

    @Delete("/api/issues/:id")
    public void delete(HttpContext ctx, @PathParam("id") Long id) {
        issueService.deleteIssue(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @Get("/api/issues/:id/history")
    public void history(HttpContext ctx, @PathParam("id") Long id) {
        ctx.ok(issueService.getHistory(id));
    }

    @Post("/api/issues/:id/comments")
    public void addComment(HttpContext ctx, @PathParam("id") Long id) {
        CommentRequest req = ctx.body(CommentRequest.class);
        ctx.json(HttpStatus.CREATED, issueService.addComment(id, req.body()));
    }
}
