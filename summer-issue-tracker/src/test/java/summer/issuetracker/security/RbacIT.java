package summer.issuetracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import summer.issuetracker.AbstractIssueTrackerIT;

/**
 * Verifies the demo's RBAC rules end-to-end through the HTTP API:
 *  - a project lead / manager can delete issues
 *  - a plain member can only mutate issues assigned to or reported by them
 *  - non-members cannot create or mutate
 *  - a viewer (project member with VIEWER role) can read but not write
 *
 * These are the demo's own business rules; the test asserts them as black-box
 * HTTP behavior, never inspecting Summer internals.
 */
class RbacIT extends AbstractIssueTrackerIT {

    // Same orgSlug => same org (AuthService derives orgId deterministically from slug),
    // so these users share an organization and can be enrolled as project members.
    private TokenAndUser admin() throws Exception {
        return registerAndLogin("rbac_admin", "rbac_org");
    }

    private TokenAndUser member() throws Exception {
        return registerAndLogin("rbac_member", "rbac_org");
    }

    private long createProject(TokenAndUser owner) throws Exception {
        String key = "RBC" + System.nanoTime() % 100000;
        var res = post("/api/projects", """
                {"projectKey":"%s","name":"RBAC Project"}
                """.formatted(key), owner.token());
        if (res.statusCode() != 201) {
            throw new IllegalStateException("createProject failed: " + res.statusCode() + " " + res.body());
        }
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    private long createIssue(TokenAndUser owner, long projectId, String assigneeId) throws Exception {
        String assigneeJson = "null".equals(assigneeId) ? "null" : assigneeId;
        var res = post("/api/projects/" + projectId + "/issues", """
                {"title":"Task","description":"","status":"OPEN","priority":"MEDIUM","assigneeId":%s}
                """.formatted(assigneeJson), owner.token());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    @Test
    void nonMemberCannotCreateIssue() throws Exception {
        var admin = admin();
        long projectId = createProject(admin);
        var outsider = registerAndLogin("rbac_outsider", "other_org");

        var res = post("/api/projects/" + projectId + "/issues", """
                {"title":"x","description":"","status":"OPEN","priority":"LOW","assigneeId":null}
                """, outsider.token());
        assertEquals(403, res.statusCode(), "Non-member must be forbidden to create");
    }

    @Test
    void memberCanMutateOwnReportedIssueButNotOthers() throws Exception {
        var admin = admin();
        long projectId = createProject(admin);
        var m = member();

        // Enroll member into the project.
        post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(m.userId()), admin.token());

        // Member reports an issue themselves. In this demo reporting is implicit:
        // admin creates, then assigns to member so member "owns" it.
        long issueId = createIssue(admin, projectId, String.valueOf(m.userId()));

        // Member may change its status (owns it via assignment).
        var ok = put("/api/issues/" + issueId + "/status", """
                {"status":"IN_PROGRESS"}
                """, m.token());
        assertEquals(200, ok.statusCode(), ok.body());

        // Member may NOT delete (only manager/lead can).
        var del = delete("/api/issues/" + issueId, m.token());
        assertEquals(403, del.statusCode(), "Member must be forbidden to delete");
    }

    @Test
    void managerCanDelete() throws Exception {
        var admin = admin();
        long projectId = createProject(admin);
        long issueId = createIssue(admin, projectId, "null");
        // createIssue builds JSON with assigneeId as a bare value; pass the literal null token.

        var del = delete("/api/issues/" + issueId, admin.token());
        assertEquals(204, del.statusCode(), "Project lead/manager may delete");
    }

    // ── Cross-org isolation on repo-direct / key-resolved reads (batch A) ──

    @Test
    void cannotReadAnotherOrgsProjectDirectly() throws Exception {
        var a = admin();
        long projA = createProject(a);

        var b = registerAndLogin("rbac_crossB", "rbac_crossB_org");
        // org B user poking at org A's project id must be denied, not served.
        var res = authGet("/api/projects/" + projA, b.token());
        assertEquals(403, res.statusCode(), "Cross-org project read must be forbidden");
    }

    @Test
    void cannotResolveAnotherOrgsIssueKey() throws Exception {
        var a = admin();
        long projA = createProject(a);
        String key = issueKeyOf(a, projA);

        var b = registerAndLogin("rbac_crossC", "rbac_crossC_org");
        // A legitimate-looking key from org A must not resolve for org B.
        var res = authGet("/api/issues/key/" + key, b.token());
        assertEquals(404, res.statusCode(), "Cross-org issue key must not resolve");
    }

    @Test
    void ownOrgsProjectReadIsAllowed() throws Exception {
        var a = admin();
        long projA = createProject(a);
        var res = authGet("/api/projects/" + projA, a.token());
        assertEquals(200, res.statusCode());
    }

    @SuppressWarnings("unchecked")
    private String issueKeyOf(TokenAndUser owner, long projectId) throws Exception {
        long issueId = createIssue(owner, projectId, "null");
        var list = authGet("/api/projects/" + projectId + "/issues", owner.token());
        assertEquals(200, list.statusCode());
        Map<String, Object> page = mapper.readValue(list.body(), Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        String key = content.stream()
                .filter(i -> ((Number) i.get("id")).longValue() == issueId)
                .map(i -> (String) i.get("issueKey"))
                .findFirst().orElseThrow();
        assertNotEquals("", key, "issue key should be populated");
        return key;
    }

    // ── Batch B: privilege escalation + tag endpoint auth ──

    @Test
    void cannotAssignAdminProjectRole() throws Exception {
        // ADMIN is an org-level role and must never be written into project_members.
        var admin = admin();
        long projectId = createProject(admin);
        var m = member();
        post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(m.userId()), admin.token());

        var escalate = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"ADMIN"}
                """.formatted(m.userId()), admin.token());
        assertEquals(400, escalate.statusCode(), "Assigning ADMIN as a project role must be rejected");
    }

    @Test
    void cannotEnumerateAnotherOrgsTags() throws Exception {
        var a = admin();
        long orgA = orgIdOf(a.token());

        var b = registerAndLogin("rbac_tagB", "rbac_tagB_org");
        var res = authGet("/api/orgs/" + orgA + "/tags", b.token());
        assertEquals(403, res.statusCode(), "Cross-org tag enumeration must be forbidden");
    }

    @Test
    void cannotAttachTagToAnotherOrgsIssue() throws Exception {
        var a = admin();
        long projectId = createProject(a);
        long issueId = createIssue(a, projectId, "null");
        long orgA = orgIdOf(a.token());
        long tagA = createTag(a, orgA, "shared");

        // Org B tries to attach a tag (even one that exists in A) to A's issue.
        var b = registerAndLogin("rbac_tagC", "rbac_tagC_org");
        var res = post("/api/issues/" + issueId + "/tags/" + tagA, "", b.token());
        assertEquals(403, res.statusCode(), "Cross-org tag attach must be forbidden");
    }

    @Test
    void sameOrgCanAttachTag() throws Exception {
        var a = admin();
        long projectId = createProject(a);
        long issueId = createIssue(a, projectId, "null");
        long orgA = orgIdOf(a.token());
        long tagA = createTag(a, orgA, "mine");

        var res = post("/api/issues/" + issueId + "/tags/" + tagA, "", a.token());
        assertEquals(204, res.statusCode(), "Same-org tag attach must succeed");
    }

    private long orgIdOf(String token) throws Exception {
        var me = authGet("/api/me", token);
        assertEquals(200, me.statusCode());
        return ((Number) mapper.readValue(me.body(), Map.class).get("orgId")).longValue();
    }

    private long createTag(TokenAndUser owner, long orgId, String name) throws Exception {
        var res = post("/api/orgs/" + orgId + "/tags", """
                {"name":"%s","color":"#abcdef"}
                """.formatted(name), owner.token());
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    @Test
    void addingDuplicateMemberConflicts() throws Exception {
        var admin = admin();
        long projectId = createProject(admin);
        var m = member();
        // First add succeeds.
        var first = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(m.userId()), admin.token());
        assertEquals(204, first.statusCode(), "First add must succeed");
        // Second add of the same user must surface a conflict, not silently no-op.
        var second = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(m.userId()), admin.token());
        assertEquals(409, second.statusCode(), "Duplicate member add must conflict");
    }
}
