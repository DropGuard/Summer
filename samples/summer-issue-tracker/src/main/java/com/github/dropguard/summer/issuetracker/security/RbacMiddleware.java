package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Order;
import com.github.dropguard.summer.issuetracker.issue.Issue;
import com.github.dropguard.summer.issuetracker.issue.IssueRepository;
import com.github.dropguard.summer.issuetracker.project.Project;
import com.github.dropguard.summer.issuetracker.project.ProjectRepository;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.Middleware;
import com.github.dropguard.summer.web.RequestAttributes;
import com.github.dropguard.summer.web.annotation.GlobalMiddleware;
import com.github.dropguard.summer.web.exception.ForbiddenException;
import com.github.dropguard.summer.web.exception.NotFoundException;
import com.github.dropguard.summer.web.exception.UnauthorizedException;

/**
 * The coarse-grained RBAC gate, enforced at the HTTP layer before any handler runs (the Gin-style
 * split: the actor comes from the {@code userId} request attribute, the resource id from the path).
 *
 * <p>The chain composes {@code handler = m.apply(handler)} in list order, so the LAST middleware in
 * the list runs FIRST. {@link JwtAuthMiddleware} therefore carries the HIGHER {@code @Order}
 * ({@code @Order(2)} &gt; this {@code @Order(1)}): auth populates the attribute before this gate
 * reads it. Public routes pass through; every other route requires an actor (401) and a
 * Middleware-layer rejections should throw BusinessException to be handled by the framework's
 * global error handler, ensuring consistent JSON error responses.
 *
 * <p>The fine-grained rule ("a plain member may only mutate issues they reported or are assigned
 * to") and the audit trail live in the service layer, which holds the resource at hand.
 *
 * <p>The route→scope mapping lives here because annotation controllers have no route groups: the
 * protected surface is {@code /api/projects/*}, {@code /api/issues/*}, and {@code /api/orgs/*},
 * with {@code DELETE /api/issues/:id} (deleteIssue) restricted to managers/leads.
 */
@Component
@GlobalMiddleware
@Order(1)
public class RbacMiddleware implements Middleware {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAuthorization authz;

    public RbacMiddleware(
            IssueRepository issueRepository,
            ProjectRepository projectRepository,
            ProjectAuthorization authz) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.authz = authz;
    }

    @Override
    public Handler apply(Handler handler) {
        return ctx -> {
            if (PublicRoutes.isPublic(ctx)) {
                handler.handle(ctx);
                return;
            }
            Long actorId = ctx.request().getAttribute(RequestAttributes.USER_ID);
            if (actorId == null) {
                throw new UnauthorizedException("Authentication required");
            }
            gate(ctx, actorId);
            handler.handle(ctx);
        };
    }

    private void gate(HttpContext ctx, long actorId) {
        // The middleware chain runs BEFORE route matching (unlike Gin, where the route is matched
        // first and c.Param is available) — so path params are not populated yet. The demo's route
        // shape is uniform (`/api/{scope}/{resourceId}/...`), so the id is parsed from the path
        // segments directly; the resource scope metadata lives here, not on the routes.
        String path = ctx.path();
        String[] segments = path.split("/");
        if (segments.length < 4 || !"api".equals(segments[1])) {
            return; // /api/me and anything else: actor presence is the gate.
        }
        String scope = segments[2];
        String resourceId = segments[3];
        if ("issues".equals(scope)) {
            if ("key".equals(resourceId)) {
                return; // by-key read: the controller resolves the org-scoped project itself.
            }
            long issueId = parseId(resourceId);
            Issue issue =
                    issueRepository
                            .findById(issueId)
                            .orElseThrow(() -> new NotFoundException("Issue not found"));
            Project project =
                    projectRepository
                            .findById(issue.projectId())
                            .orElseThrow(() -> new NotFoundException("Project not found"));
            if ("DELETE".equals(ctx.method().name()) && path.equals("/api/issues/" + issueId)) {
                // deleteIssue is the destructive admin action: manager or lead only.
                authz.assertCanAdminister(authz.requireActor(actorId), project);
            } else {
                authz.assertCanAccess(authz.requireActor(actorId), project);
            }
            return;
        }
        if ("projects".equals(scope)) {
            long projectId = parseId(resourceId);
            Project project =
                    projectRepository
                            .findById(projectId)
                            .orElseThrow(() -> new NotFoundException("Project not found"));
            authz.assertCanAccess(authz.requireActor(actorId), project);
            return;
        }
        if ("orgs".equals(scope)) {
            long orgId = parseId(resourceId);
            if (authz.requireActor(actorId).orgId() != orgId) {
                throw new ForbiddenException("You do not belong to this organization");
            }
        }
    }

    private static long parseId(String segment) {
        try {
            return Long.parseLong(segment);
        } catch (NumberFormatException e) {
            throw new NotFoundException("Resource not found");
        }
    }
}
