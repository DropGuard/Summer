package summer.issuetracker.tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import summer.issuetracker.AbstractIssueTrackerIT;

/**
 * Business tests for the tag feature: create org tags, attach/detach them to an
 * issue, and filter issues by tag. All assertions are black-box HTTP results —
 * no Summer internals are inspected.
 */
class TagIT extends AbstractIssueTrackerIT {

    private long orgIdOf(long projectId, String token) throws Exception {
        var res = authGet("/api/projects/" + projectId, token);
        assertEquals(200, res.statusCode());
        return ((Number) mapper.readValue(res.body(), Map.class).get("orgId")).longValue();
    }

    @Test
    void createListAttachAndFilterByTag() throws Exception {
        var owner = registerAndLogin("tag_owner", "tag_org");
        long projectId = createProject(owner.token(), "TAG", "Tag Project");
        long orgId = orgIdOf(projectId, owner.token());

        // Create two org tags.
        var t1 = post("/api/orgs/" + orgId + "/tags", """
                {"name":"backend","color":"#ff0000"}
                """, owner.token());
        assertEquals(201, t1.statusCode(), t1.body());
        long tagId = ((Number) mapper.readValue(t1.body(), Map.class).get("id")).longValue();

        var t2 = post("/api/orgs/" + orgId + "/tags", """
                {"name":"frontend","color":"#00ff00"}
                """, owner.token());
        assertEquals(201, t2.statusCode(), t2.body());

        // Duplicate tag name in the same org must be rejected.
        var dup = post("/api/orgs/" + orgId + "/tags", """
                {"name":"backend","color":"#000000"}
                """, owner.token());
        assertEquals(400, dup.statusCode(), "Duplicate tag name must be rejected");

        // Create an issue and attach the backend tag.
        long issueId = createIssue(projectId, owner.token(), "Login broken");
        var attach = post("/api/issues/" + issueId + "/tags/" + tagId, "", owner.token());
        assertEquals(204, attach.statusCode(), attach.body());

        // The issue must list the attached tag.
        var issueTags = authGet("/api/issues/" + issueId + "/tags", owner.token());
        assertEquals(200, issueTags.statusCode());
        List<?> tags = mapper.readValue(issueTags.body(), List.class);
        assertEquals(1, tags.size());
        assertEquals("backend", ((Map<?, ?>) tags.get(0)).get("name"));

        // Filtering issues by tagId returns the tagged issue (paginated envelope).
        var filtered = authGet("/api/projects/" + projectId + "/issues?tagId=" + tagId, owner.token());
        assertEquals(200, filtered.statusCode());
        List<?> issues = pageContent(filtered.body());
        assertEquals(1, issues.size());
        assertEquals("Login broken", ((Map<?, ?>) issues.get(0)).get("title"));

        // Detach and confirm it is gone.
        var detach = delete("/api/issues/" + issueId + "/tags/" + tagId, owner.token());
        assertEquals(204, detach.statusCode());
        var after = authGet("/api/issues/" + issueId + "/tags", owner.token());
        List<?> afterTags = mapper.readValue(after.body(), List.class);
        assertTrue(afterTags.isEmpty(), "Tag should be detached");
    }

    @Test
    void paginationStaysCorrectWhenIssueHasMultipleMatchingTags() throws Exception {
        var owner = registerAndLogin("tag_pager", "tag_pager_org");
        long projectId = createProject(owner.token(), "PAG", "Pager Project");
        long orgId = orgIdOf(projectId, owner.token());

        long tagA = ((Number) mapper.readValue(
                post("/api/orgs/" + orgId + "/tags", """
                        {"name":"alpha","color":"#111111"}
                        """, owner.token()).body(), Map.class).get("id")).longValue();
        long tagB = ((Number) mapper.readValue(
                post("/api/orgs/" + orgId + "/tags", """
                        {"name":"beta","color":"#222222"}
                        """, owner.token()).body(), Map.class).get("id")).longValue();

        // One issue carrying TWO matching tags — the case that broke naive
        // JOIN / post-filter pagination (duplicated rows, wrong total).
        long issueId = createIssue(projectId, owner.token(), "Multi-tagged");
        assertEquals(204, post("/api/issues/" + issueId + "/tags/" + tagA, "", owner.token()).statusCode());
        assertEquals(204, post("/api/issues/" + issueId + "/tags/" + tagB, "", owner.token()).statusCode());

        // Filter by tagA with a tiny page window (size 1). The issue must appear
        // exactly once and the total must be 1, never 2.
        var page = authGet("/api/projects/" + projectId + "/issues?tagId=" + tagA + "&size=1", owner.token());
        assertEquals(200, page.statusCode());
        Map<String, Object> envelope = mapper.readValue(page.body(), Map.class);
        assertEquals(1L, ((Number) envelope.get("total")).longValue(), "total must not count tags");
        @SuppressWarnings("unchecked")
        List<?> content = (List<?>) envelope.get("content");
        assertEquals(1, content.size());
        assertEquals("Multi-tagged", ((Map<?, ?>) content.get(0)).get("title"));
    }

    @Test
    void orgTagsAreIsolatedBetweenOrgs() throws Exception {
        var a = registerAndLogin("tag_orgA", "tag_orgA");
        long pa = createProject(a.token(), "A", "Org A project");
        long oa = orgIdOf(pa, a.token());
        long tagA = ((Number) mapper.readValue(
                post("/api/orgs/" + oa + "/tags", """
                        {"name":"shared","color":"#123456"}
                        """, a.token()).body(), Map.class).get("id")).longValue();

        var b = registerAndLogin("tag_orgB", "tag_orgB");
        long pb = createProject(b.token(), "B", "Org B project");
        long ob = orgIdOf(pb, b.token());
        // Org B must NOT see org A's tag when listing its own tags.
        var bTags = authGet("/api/orgs/" + ob + "/tags", b.token());
        assertEquals(200, bTags.statusCode());
        List<?> list = mapper.readValue(bTags.body(), List.class);
        boolean seesA = list.stream().anyMatch(t -> ((Number) ((Map<?, ?>) t).get("id")).longValue() == tagA);
        assertFalse(seesA, "Org B must not see Org A's tags");
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
