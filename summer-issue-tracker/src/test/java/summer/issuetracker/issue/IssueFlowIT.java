package summer.issuetracker.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import summer.issuetracker.AbstractIssueTrackerIT;

/**
 * End-to-end issue lifecycle: create -> mutate status -> issue history recorded.
 * These are the demo's own business behaviors, asserted as black-box HTTP results
 * (status codes, issue keys, history rows). They do NOT probe Summer internals — the
 * issue change history is a demo feature (every mutation must leave a matching
 * issue_history row), and we check that it works from the outside, not how the
 * framework does it.
 */
class IssueFlowIT extends AbstractIssueTrackerIT {

    @Test
    void createThenMutateLeavesAuditTrail() throws Exception {
        var owner = registerAndLogin("flow_owner", "flow_org");
        // Create a project (owner becomes MANAGER + lead).
        var projectRes = post("/api/projects", """
                {"projectKey":"FLOW","name":"Flow Project"}
                """, owner.token());
        assertEquals(201, projectRes.statusCode(), projectRes.body());
        long projectId = ((Number) mapper.readValue(projectRes.body(), Map.class).get("id")).longValue();

        // Create an issue.
        var createRes = post("/api/projects/" + projectId + "/issues", """
                {"title":"Broken login","description":"Auth fails","status":"OPEN","priority":"HIGH","assigneeId":null}
                """, owner.token());
        assertEquals(201, createRes.statusCode(), createRes.body());
        Map<?, ?> issue = mapper.readValue(createRes.body(), Map.class);
        long issueId = ((Number) issue.get("id")).longValue();
        assertEquals("FLOW-1", issue.get("issueKey"));

        // Change status.
        var statusRes = put("/api/issues/" + issueId + "/status", """
                {"status":"IN_PROGRESS"}
                """, owner.token());
        assertEquals(200, statusRes.statusCode(), statusRes.body());
        assertEquals("IN_PROGRESS", mapper.readValue(statusRes.body(), Map.class).get("status"));

        // History must contain CREATED + STATUS_CHANGED.
        var historyRes = authGet("/api/issues/" + issueId + "/history", owner.token());
        assertEquals(200, historyRes.statusCode());
        List<?> history = mapper.readValue(historyRes.body(), List.class);
        assertEquals(2, history.size(), "Expected CREATED + STATUS_CHANGED audit rows");
        assertTrue(history.stream().anyMatch(h -> "CREATED".equals(((Map<?, ?>) h).get("action"))));
        assertTrue(history.stream().anyMatch(h -> "STATUS_CHANGED".equals(((Map<?, ?>) h).get("action"))));
    }

    @Test
    void unauthenticatedCreateIsRejected() throws Exception {
        var res = post("/api/projects/1/issues", """
                {"title":"x","description":"y","status":"OPEN","priority":"LOW","assigneeId":null}
                """, null);
        assertEquals(401, res.statusCode());
    }

    @Test
    void dynamicFilterByStatusAndPriority() throws Exception {
        var owner = registerAndLogin("filter_owner", "filter_org");
        var projectRes = post("/api/projects", """
                {"projectKey":"FLT","name":"Filter Project"}
                """, owner.token());
        long projectId = ((Number) mapper.readValue(projectRes.body(), Map.class).get("id")).longValue();

        post("/api/projects/" + projectId + "/issues", """
                {"title":"A","description":"","status":"OPEN","priority":"HIGH","assigneeId":null}
                """, owner.token());
        post("/api/projects/" + projectId + "/issues", """
                {"title":"B","description":"","status":"IN_PROGRESS","priority":"LOW","assigneeId":null}
                """, owner.token());

        var res = authGet("/api/projects/" + projectId + "/issues?status=OPEN&priority=HIGH", owner.token());
        assertEquals(200, res.statusCode());
        List<?> issues = pageContent(res.body());
        assertEquals(1, issues.size());
        assertEquals("A", ((Map<?, ?>) issues.get(0)).get("title"));
    }

    @Test
    void invalidStatusIsRejected() throws Exception {
        var owner = registerAndLogin("badstatus_owner", "badstatus_org");
        var projectRes = post("/api/projects", """
                {"projectKey":"BAD","name":"Bad Project"}
                """, owner.token());
        long projectId = ((Number) mapper.readValue(projectRes.body(), Map.class).get("id")).longValue();
        var createRes = post("/api/projects/" + projectId + "/issues", """
                {"title":"X","description":"","status":"NONSENSE","priority":"LOW","assigneeId":null}
                """, owner.token());
        assertEquals(400, createRes.statusCode(), "Invalid status must be rejected with 400");
    }

    @Test
    void commentsAreAttachedToIssue() throws Exception {
        var owner = registerAndLogin("comment_owner", "comment_org");
        long projectId = createProject(owner.token(), "CMT", "Comment Project");
        long issueId = createIssue(projectId, owner.token(), "Need docs");

        var c1 = post("/api/issues/" + issueId + "/comments", """
                {"body":"Drafting now"}
                """, owner.token());
        assertEquals(201, c1.statusCode(), c1.body());
        var c2 = post("/api/issues/" + issueId + "/comments", """
                {"body":"PR opened"}
                """, owner.token());
        assertEquals(201, c2.statusCode(), c2.body());

        var detail = authGet("/api/issues/" + issueId, owner.token());
        assertEquals(200, detail.statusCode());
        Map<?, ?> body = mapper.readValue(detail.body(), Map.class);
        assertEquals(2, body.get("commentCount"));
        List<?> comments = (List<?>) body.get("comments");
        assertEquals(2, comments.size());
        assertTrue(comments.stream().anyMatch(c -> "Drafting now".equals(((Map<?, ?>) c).get("body"))));
    }

    private long createProject(String token, String key, String name) throws Exception {
        var res = post("/api/projects", """
                {"projectKey":"%s","name":"%s"}
                """.formatted(key, name), token);
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    private long createIssue(long projectId, String token, String title) throws Exception {
        var res = post("/api/projects/" + projectId + "/issues", """
                {"title":"%s","description":"","status":"OPEN","priority":"LOW","assigneeId":null}
                """.formatted(title), token);
        assertEquals(201, res.statusCode(), res.body());
        return ((Number) mapper.readValue(res.body(), Map.class).get("id")).longValue();
    }

    /** The list/search endpoint returns a paginated envelope; extract its content. */
    @SuppressWarnings("unchecked")
    private List<?> pageContent(String body) throws Exception {
        Map<String, Object> page = mapper.readValue(body, Map.class);
        return (List<?>) page.get("content");
    }
}
