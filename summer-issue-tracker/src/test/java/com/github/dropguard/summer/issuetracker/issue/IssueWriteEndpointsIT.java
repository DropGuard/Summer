package com.github.dropguard.summer.issuetracker.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.dropguard.summer.issuetracker.AbstractIssueTrackerIT;

/**
 * Fine-grained write authorization on the issue PUT endpoints — the rules that
 * {@link com.github.dropguard.summer.issuetracker.security.ProjectAuthorization#assertOwns} enforces.
 * {@link com.github.dropguard.summer.issuetracker.security.RbacIT} covers create/delete; here we hit
 * update / status / assign / priority directly and assert that a plain member may
 * only mutate issues assigned to or reported by them, a viewer may not write at
 * all, and an outsider is forbidden entirely.
 */
class IssueWriteEndpointsIT extends AbstractIssueTrackerIT {

    // Same orgSlug => same org.
    private TokenAndUser owner() throws Exception {
        return registerAndLogin("wr_owner", "wr_org");
    }

    private TokenAndUser member() throws Exception {
        return registerAndLogin("wr_member", "wr_org");
    }

    private TokenAndUser viewer() throws Exception {
        return registerAndLogin("wr_viewer", "wr_org");
    }

    private TokenAndUser outsider() throws Exception {
        return registerAndLogin("wr_out", "other_org");
    }

    private long createProject(TokenAndUser u) throws Exception {
        String key = "WR" + System.nanoTime() % 100000;
        var res = post("/api/projects", """
                {"projectKey":"%s","name":"WR"}
                """.formatted(key), u.token());
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    private long createIssue(long projectId, String token, Long assigneeId) throws Exception {
        String a = assigneeId == null ? "null" : String.valueOf(assigneeId);
        var res = post("/api/projects/" + projectId + "/issues", """
                {"title":"T","description":"","status":"OPEN","priority":"LOW","assigneeId":%s}
                """.formatted(a), token);
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    @Test
    void managerCanMutateAnyIssue() throws Exception {
        var o = owner();
        long projectId = createProject(o);
        long issueId = createIssue(projectId, o.token(), null);

        assertEquals(200, put("/api/issues/" + issueId + "/status",
                "{\"status\":\"IN_PROGRESS\"}", o.token()).statusCode());
        assertEquals(200, put("/api/issues/" + issueId + "/priority",
                "{\"priority\":\"HIGH\"}", o.token()).statusCode());
    }

    @Test
    void memberCanMutateOnlyOwnIssues() throws Exception {
        var o = owner();
        var m = member();
        // Enroll member with MEMBER role.
        long projectId = createProject(o);
        assertEquals(204, post("/api/projects/" + projectId + "/members",
                "{\"userId\":%d,\"role\":\"MEMBER\"}".formatted(m.userId()), o.token()).statusCode());

        long own = createIssue(projectId, o.token(), m.userId()); // assigned to member
        long others = createIssue(projectId, o.token(), o.userId()); // assigned to owner

        // Member may change status of the issue assigned to them.
        assertEquals(200, put("/api/issues/" + own + "/status",
                "{\"status\":\"IN_PROGRESS\"}", m.token()).statusCode(),
                "Member may mutate an issue assigned to them");
        // Member may NOT change status of an issue assigned to someone else.
        assertEquals(403, put("/api/issues/" + others + "/status",
                "{\"status\":\"IN_PROGRESS\"}", m.token()).statusCode(),
                "Member may not mutate another's issue");
    }

    @Test
    void viewerCannotWrite() throws Exception {
        var o = owner();
        var v = viewer();
        long projectId = createProject(o);
        assertEquals(204, post("/api/projects/" + projectId + "/members",
                "{\"userId\":%d,\"role\":\"VIEWER\"}".formatted(v.userId()), o.token()).statusCode());
        long issueId = createIssue(projectId, o.token(), null);

        assertEquals(403, put("/api/issues/" + issueId + "/status",
                "{\"status\":\"IN_PROGRESS\"}", v.token()).statusCode(),
                "Viewer role is read-only");
        assertEquals(403, put("/api/issues/" + issueId + "/assign",
                "{\"assigneeId\":%d}".formatted(v.userId()), v.token()).statusCode());
    }

    @Test
    void outsiderCannotMutate() throws Exception {
        var o = owner();
        var out = outsider();
        long projectId = createProject(o);
        long issueId = createIssue(projectId, o.token(), null);

        assertEquals(403, put("/api/issues/" + issueId + "/status",
                "{\"status\":\"IN_PROGRESS\"}", out.token()).statusCode(),
                "Outsider must be forbidden to mutate a foreign project's issue");
    }
}
