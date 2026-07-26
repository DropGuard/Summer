package com.github.dropguard.summer.issuetracker.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.dropguard.summer.issuetracker.AbstractIssueTrackerIT;

/**
 * Project management endpoints: authz (tenant isolation + membership) and the
 * non-obvious rules (auto manager on create, role whitelist, duplicate-member 409).
 * {@code members} previously had no guard — any authenticated user could enumerate
 * another project's roster — so it is asserted here as 403 for outsiders.
 */
class ProjectControllerIT extends AbstractIssueTrackerIT {

    // Same orgSlug => same org (AuthService derives orgId deterministically).
    private TokenAndUser owner() throws Exception {
        return registerAndLogin("proj_owner", "proj_org");
    }

    private TokenAndUser mate() throws Exception {
        return registerAndLogin("proj_mate", "proj_org");
    }

    private TokenAndUser outsider() throws Exception {
        return registerAndLogin("proj_out", "other_org");
    }

    private long createProject(TokenAndUser u) throws Exception {
        String key = "PJ" + System.nanoTime() % 100000;
        var res = post("/api/projects", """
                {"projectKey":"%s","name":"Proj"}
                """.formatted(key), u.token());
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    @Test
    void createMakesCallerManager() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        var members = authGet("/api/projects/" + projectId + "/members", o.token());
        assertEquals(200, members.statusCode());
        List<Map<String, Object>> roster = mapper.readValue(members.body(), List.class);
        assertEquals(1, roster.size(), "Creator is the only initial member");
        assertEquals("MANAGER", roster.get(0).get("role"));
        assertEquals(o.userId(), ((Number) roster.get(0).get("userId")).longValue());
    }

    @Test
    void listMineIsScopedToCaller() throws Exception {
        var o = owner();
        createProject(o);
        var other = registerAndLogin("proj_other_owner", "proj_org");
        createProject(other);

        var mine = authGet("/api/projects", o.token());
        assertEquals(200, mine.statusCode());
        List<Map<String, Object>> projects = mapper.readValue(mine.body(), List.class);
        assertEquals(1, projects.size(), "Only the caller's own project is returned");
    }

    @Test
    void getForbidsCrossOrg() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        var out = outsider();

        var res = authGet("/api/projects/" + projectId, out.token());
        assertEquals(403, res.statusCode(), "Cross-org project read must be forbidden");
    }

    @Test
    void membersForbidsCrossOrg() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        var out = outsider();

        var res = authGet("/api/projects/" + projectId + "/members", out.token());
        assertEquals(403, res.statusCode(), "Cross-org roster enumeration must be forbidden");
    }

    @Test
    void addMemberRejectsNonProjectRole() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        var mate = mate();

        var res = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"ADMIN"}
                """.formatted(mate.userId()), o.token());
        assertEquals(400, res.statusCode(), "ADMIN (org role) must not be writable as a project role");
    }

    @Test
    void addMemberRejectsDuplicate() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        var mate = mate();

        var first = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(mate.userId()), o.token());
        assertEquals(204, first.statusCode(), first.body());

        var second = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(mate.userId()), o.token());
        assertEquals(409, second.statusCode(), "Re-adding an existing member must conflict");
    }

    @Test
    void addMemberForbidsCrossOrgActor() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        var out = outsider();

        var res = post("/api/projects/" + projectId + "/members", """
                {"userId":%d,"role":"MEMBER"}
                """.formatted(out.userId()), out.token());
        assertEquals(403, res.statusCode(), "Outsider cannot add members to a foreign project");
    }
}
