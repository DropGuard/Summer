package summer.issuetracker.security;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.core.Component;
import summer.issuetracker.audit.SystemAudit;
import summer.issuetracker.audit.SystemAuditRepository;
import summer.issuetracker.common.BusinessException;
import summer.issuetracker.common.IdGenerator;
import summer.issuetracker.issue.Issue;
import summer.issuetracker.issue.IssueRepository;
import summer.web.RequestContextHolder;

/**
 * Method-level RBAC for {@code IssueService}, bound via {@link RequireRole}
 * (declared at the interface level, so every method is intercepted).
 *
 * <p>
 * This is the demo's proof that Summer's AOP can enforce request-aware
 * authorization: the current user is read from {@link RequestContextHolder}
 * (populated by the framework's auth middleware), and the resource's owning
 * project is resolved from the {@link ResourceScope} annotation on the
 * intercepted method — so the service signatures stay clean and carry no
 * {@code actorId}, and the interceptor no longer depends on method names or
 * argument positions (the previous version switched on {@code method.name()}).
 * </p>
 *
 * <p>
 * The interceptor enforces the <em>coarse-grained</em> gate: tenant isolation
 * plus project membership / manager-or-lead. The <em>fine-grained</em> rule ("a
 * plain member may only mutate issues they reported or are assigned to") lives
 * in the service, which also reads the current user from the holder. All
 * membership / manager / lead rules are delegated to {@link ProjectAuthorization}
 * so they are defined in exactly one place.
 * </p>
 *
 * <p>
 * After the guarded call proceeds, the interceptor also writes a Jira-style
 * <em>system</em> audit event (independent of the issue's change history) for
 * every mutating method. Because {@link SystemAudit} is self-contained — the
 * target identity is frozen into the row at write time and has no foreign key to
 * the live entity — these events survive deletion of the issue they describe.
 * </p>
 */
@Interceptor
@RequireRole
@Component
public class RbacInterceptor implements MethodInterceptor {

    private final ProjectAuthorization authz;
    private final IssueRepository issueRepository;
    private final SystemAuditRepository auditRepository;
    private final IdGenerator idGenerator;

    public RbacInterceptor(ProjectAuthorization authz, IssueRepository issueRepository,
            SystemAuditRepository auditRepository, IdGenerator idGenerator) {
        this.authz = authz;
        this.issueRepository = issueRepository;
        this.auditRepository = auditRepository;
        this.idGenerator = idGenerator;
    }

    /** Mutating IssueService methods that should emit a system audit event. */
    private static boolean isAuditable(String methodName) {
        return switch (methodName) {
            case "createIssue", "updateStatus", "assign", "changePriority",
                 "updateIssue", "deleteIssue", "addComment" -> true;
            default -> false;
        };
    }

    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        Long actorId = RequestContextHolder.currentUserId();
        if (actorId == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        // The actor may be an ADMIN of their own org; that is resolved inside
        // ProjectAuthorization, which still enforces tenant isolation first.
        var actor = authz.requireActor(actorId);

        ResourceScope.Kind kind = resourceKind(chain);
        long resourceId = ((Number) chain.getArguments()[0]).longValue();
        var project = authz.resolveProject(kind, resourceId);

        authz.assertSameOrg(actor, project);
        if ("deleteIssue".equals(chain.method().name())) {
            // Destructive admin action: manager or lead only.
            authz.assertCanAdminister(actor, project);
        } else {
            authz.assertCanAccess(actor, project);
        }

        Object result = chain.proceed();

        if (isAuditable(chain.method().name())) {
            // Default target is the resolved resource (project for PROJECT-scoped
            // methods, the issue for ISSUE-scoped ones).
            String targetType = kind == ResourceScope.Kind.PROJECT ? "PROJECT" : "ISSUE";
            long targetId = resourceId;
            String targetKey = null;
            if (kind == ResourceScope.Kind.PROJECT) {
                targetKey = project.projectKey();
            } else {
                targetKey = issueRepository.findById(resourceId).map(Issue::issueKey).orElse(null);
            }
            // createIssue is PROJECT-scoped for auth (its argument is the project),
            // but its *product* is an issue — record the created issue as the target.
            if (result instanceof Issue created) {
                targetType = "ISSUE";
                targetId = created.id();
                targetKey = created.issueKey();
            }
            auditRepository.insert(new SystemAudit(
                    idGenerator.nextId(), actor.orgId(), actor.id(),
                    chain.method().name().toUpperCase(), targetType, targetId, targetKey,
                    OffsetDateTime.now()));
        }
        return result;
    }

    /**
     * Reads {@link ResourceScope} from the intercepted interface method. Reading
     * annotation members is a business-module concern; the framework documents
     * that the target method may be reflected on directly for this purpose.
     */
    private ResourceScope.Kind resourceKind(InterceptorChain chain) {
        String name = chain.method().name();
        for (Class<?> iface : chain.getTarget().getClass().getInterfaces()) {
            for (Method m : iface.getMethods()) {
                if (m.getName().equals(name) && m.isAnnotationPresent(ResourceScope.class)) {
                    return m.getAnnotation(ResourceScope.class).value();
                }
            }
        }
        throw new IllegalStateException(
                "No @ResourceScope on " + name + " — RBAC cannot resolve the project");
    }
}
