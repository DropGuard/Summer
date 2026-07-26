package summer.issuetracker.project;

import java.util.List;

import summer.core.Component;
import summer.issuetracker.audit.SystemAudit;
import summer.issuetracker.audit.SystemAuditRepository;
import summer.issuetracker.common.BusinessException;
import summer.issuetracker.common.IdGenerator;
import summer.issuetracker.issue.IssueService;
import summer.issuetracker.security.ProjectAuthorization;
import summer.issuetracker.security.SecurityContext;
import summer.issuetracker.user.User;
import summer.issuetracker.user.UserRepository;
import summer.web.HttpContext;
import summer.web.HttpStatus;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

@RestController
@Component
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IdGenerator idGenerator;
    private final IssueService issueService;
    private final ProjectAuthorization authz;
    private final SystemAuditRepository auditRepository;

    public ProjectController(ProjectRepository projectRepository, UserRepository userRepository,
            IdGenerator idGenerator, IssueService issueService, ProjectAuthorization authz,
            SystemAuditRepository auditRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.idGenerator = idGenerator;
        this.issueService = issueService;
        this.authz = authz;
        this.auditRepository = auditRepository;
    }

    public record CreateProjectRequest(String projectKey, String name) {}

    @Post("/api/projects")
    public void create(HttpContext ctx) {
        CreateProjectRequest req = ctx.body(CreateProjectRequest.class);
        long userId = SecurityContext.currentUserId();
        User lead = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("User"));
        long id = idGenerator.nextId();
        Project project = new Project(id, lead.orgId(), req.projectKey(), req.name(), userId,
                java.time.OffsetDateTime.now());
        projectRepository.insert(project);
        projectRepository.addMember(id, userId, "MANAGER");
        auditRepository.insert(new SystemAudit(idGenerator.nextId(), lead.orgId(), userId,
                "PROJECT_CREATED", "PROJECT", id, req.projectKey(), java.time.OffsetDateTime.now()));
        ctx.json(HttpStatus.CREATED, project);
    }

    @Get("/api/projects")
    public void listMine(HttpContext ctx) {
        ctx.ok(projectRepository.findByMember(SecurityContext.currentUserId()));
    }

    @Get("/api/projects/:id")
    public void get(HttpContext ctx, @PathParam("id") Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Project"));
        // Resource-scoped read: enforce tenant isolation + project membership here,
        // mirroring RbacInterceptor's coarse-grained gate. ProjectRepository is hit
        // directly (no proxied ProjectService), so the check cannot live on a proxy.
        User actor = userRepository.findById(SecurityContext.currentUserId())
                .orElseThrow(() -> BusinessException.unauthorized("Unknown actor"));
        authz.assertSameOrg(actor, project);
        authz.assertCanAccess(actor, project);
        ctx.ok(project);
    }

    @Post("/api/projects/:id/members")
    public void addMember(HttpContext ctx, @PathParam("id") Long id) {
        record Req(Long userId, String role) {}
        Req req = ctx.body(Req.class);
        Project project = projectRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Project"));
        User actor = userRepository.findById(SecurityContext.currentUserId()).orElseThrow();
        authz.assertSameOrg(actor, project);
        // Only a manager or lead may add members — delegated to the shared authz rules.
        authz.assertCanAdminister(actor, project);
        if (userRepository.findById(req.userId()).isEmpty()) {
            throw BusinessException.notFound("User");
        }
        // The caller-supplied role must be a valid *project* role; ADMIN is an
        // org-level role and must never be written into project_members.
        if (!isValidProjectRole(req.role())) {
            throw BusinessException.badRequest("Invalid project role: " + req.role());
        }
        if (projectRepository.findMember(id, req.userId()).isPresent()) {
            throw BusinessException.conflict("User is already a member of this project");
        }
        projectRepository.addMember(id, req.userId(), req.role());
        auditRepository.insert(new SystemAudit(idGenerator.nextId(), actor.orgId(), actor.id(),
                "MEMBER_ADDED", "PROJECT", id, project.projectKey(), java.time.OffsetDateTime.now()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static boolean isValidProjectRole(String role) {
        return role != null && (role.equals("MEMBER") || role.equals("MANAGER") || role.equals("VIEWER"));
    }

    @Get("/api/projects/:id/members")
    public void members(HttpContext ctx, @PathParam("id") Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Project"));
        // Same resource-scoped read guard as get(): tenant isolation + membership,
        // enforced here because ProjectRepository is hit directly (no proxied service).
        User actor = userRepository.findById(SecurityContext.currentUserId())
                .orElseThrow(() -> BusinessException.unauthorized("Unknown actor"));
        authz.assertSameOrg(actor, project);
        authz.assertCanAccess(actor, project);
        List<ProjectMember> members = projectRepository.findMembers(id);
        ctx.ok(members);
    }
}
