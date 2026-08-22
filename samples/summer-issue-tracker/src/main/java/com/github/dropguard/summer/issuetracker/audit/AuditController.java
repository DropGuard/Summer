package com.github.dropguard.summer.issuetracker.audit;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.issuetracker.common.BusinessException;
import com.github.dropguard.summer.issuetracker.security.Actors;
import com.github.dropguard.summer.issuetracker.user.User;
import com.github.dropguard.summer.issuetracker.user.UserRepository;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.RestController;
import java.util.List;

/**
 * Read access to the Jira-style system audit log. Org-scoped: a caller may only read their own
 * organization's audit events. The events are independent of the live business rows they describe,
 * so they remain queryable after the described issue, project, or member is deleted.
 */
@RestController
@Component
public class AuditController {

    private final SystemAuditRepository auditRepository;
    private final UserRepository userRepository;

    public AuditController(SystemAuditRepository auditRepository, UserRepository userRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    @Get("/api/orgs/:orgId/audit")
    public void listByOrg(HttpContext ctx, @PathParam("orgId") Long orgId) {
        User actor =
                userRepository
                        .findById(Actors.require(ctx))
                        .orElseThrow(() -> BusinessException.unauthorized("Unknown actor"));
        if (!actor.orgId().equals(orgId)) {
            throw BusinessException.forbidden(
                    "You can only view your own organization's audit log");
        }
        List<SystemAudit> events = auditRepository.findByOrg(orgId);
        ctx.ok(events);
    }
}
