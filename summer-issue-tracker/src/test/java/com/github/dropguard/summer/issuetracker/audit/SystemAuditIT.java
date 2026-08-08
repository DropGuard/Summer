package com.github.dropguard.summer.issuetracker.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.issuetracker.AbstractIssueTrackerIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * System audit log (Jira-style) is independent of the business entities it describes: it survives
 * deletion of the issue/project/member and is org-scoped.
 */
class SystemAuditIT extends AbstractIssueTrackerIT {

    @Test
    @SuppressWarnings("unchecked")
    void systemAuditSurvivesIssueDeletion() throws Exception {
        var owner = registerAndLogin("audit_owner", "audit_org");
        long orgId = orgIdOf(owner.token());
        long projectId = createProject(owner.token(), "AUDT", "Audit Project");

        // Create an issue, then delete it.
        var created =
                post(
                        "/api/projects/" + projectId + "/issues",
                        """
                        {"title":"To be deleted","description":"","status":"OPEN","priority":"LOW","assigneeId":null}
                        """,
                        owner.token());
        assertEquals(201, created.statusCode(), created.body());
        long issueId = ((Number) mapper.readValue(created.body(), Map.class).get("id")).longValue();
        var del = delete("/api/issues/" + issueId, owner.token());
        assertEquals(204, del.statusCode(), "Manager may delete");

        // The issue row is gone, but the system audit trail is intact.
        var audit = authGet("/api/orgs/" + orgId + "/audit", owner.token());
        assertEquals(200, audit.statusCode());
        List<Map<String, Object>> events = mapper.readValue(audit.body(), List.class);
        assertTrue(
                events.stream()
                        .anyMatch(
                                e ->
                                        "CREATEISSUE".equals(e.get("action"))
                                                && ((Number) e.get("targetId")).longValue()
                                                        == issueId),
                "CREATEISSUE event must persist");
        assertTrue(
                events.stream()
                        .anyMatch(
                                e ->
                                        "DELETEISSUE".equals(e.get("action"))
                                                && ((Number) e.get("targetId")).longValue()
                                                        == issueId),
                "DELETEISSUE event must persist");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotReadAnotherOrgsAudit() throws Exception {
        var a = registerAndLogin("audit_a", "audit_a_org");
        long orgA = orgIdOf(a.token());

        var b = registerAndLogin("audit_b", "audit_b_org");
        var res = authGet("/api/orgs/" + orgA + "/audit", b.token());
        assertEquals(403, res.statusCode(), "Cross-org audit read must be forbidden");
    }

    @Test
    @SuppressWarnings("unchecked")
    void projectCreationAndMemberAddAreAudited() throws Exception {
        var admin = registerAndLogin("audit_admin", "audit_admin_org");
        long orgId = orgIdOf(admin.token());
        long projectId = createProject(admin.token(), "AUDX", "Audited Project");

        var member = registerAndLogin("audit_member", "audit_admin_org");
        var add =
                post(
                        "/api/projects/" + projectId + "/members",
                        """
                        {"userId":%d,"role":"MEMBER"}
                        """
                                .formatted(member.userId()),
                        admin.token());
        assertEquals(204, add.statusCode());

        var audit = authGet("/api/orgs/" + orgId + "/audit", admin.token());
        assertEquals(200, audit.statusCode());
        List<Map<String, Object>> events = mapper.readValue(audit.body(), List.class);
        assertTrue(
                events.stream().anyMatch(e -> "PROJECT_CREATED".equals(e.get("action"))),
                "Project creation must be audited");
        assertTrue(
                events.stream().anyMatch(e -> "MEMBER_ADDED".equals(e.get("action"))),
                "Member addition must be audited");
    }

    private long orgIdOf(String token) throws Exception {
        var me = authGet("/api/me", token);
        assertEquals(200, me.statusCode());
        return ((Number) mapper.readValue(me.body(), Map.class).get("orgId")).longValue();
    }

    private long createProject(String token, String key, String name) throws Exception {
        var res =
                post(
                        "/api/projects",
                        """
                        {"projectKey":"%s","name":"%s"}
                        """
                                .formatted(key, name),
                        token);
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }
}
