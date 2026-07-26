package summer.issuetracker.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import summer.issuetracker.AbstractIssueTrackerIT;

/**
 * Regression guard for the issue_key generation fix: keys come from an atomic
 * per-project counter (project_counters via UPDATE ... RETURNING), not
 * count(*)+1. Under concurrent creates the old approach collided on the UNIQUE
 * constraint and surfaced as a 500. This asserts every concurrent create succeeds
 * and yields a distinct key.
 */
class IssueKeyConcurrencyIT extends AbstractIssueTrackerIT {

    @Test
    void concurrentCreatesYieldDistinctKeys() throws Exception {
        var owner = registerAndLogin("conc_owner", "conc_org");
        String key = "CNC" + System.nanoTime() % 100000;
        var projectRes = post("/api/projects", """
                {"projectKey":"%s","name":"Conc"}
                """.formatted(key), owner.token());
        assertEquals(201, projectRes.statusCode(), projectRes.body());
        long projectId = ((Number) mapper.readValue(projectRes.body(), Map.class).get("id")).longValue();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<java.lang.Integer>> futures = new ArrayList<>();
        List<String> keys = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                var res = post("/api/projects/" + projectId + "/issues", """
                        {"title":"T","description":"","status":"OPEN","priority":"LOW","assigneeId":null}
                        """, owner.token());
                return res.statusCode();
            }));
        }

        start.countDown();
        for (var f : futures) {
            assertEquals(201, f.get(10, TimeUnit.SECONDS), "Concurrent create must not 500/409");
        }
        pool.shutdown();

        // Re-read all issues and assert distinct keys.
        var list = authGet("/api/projects/" + projectId + "/issues", owner.token());
        assertEquals(200, list.statusCode());
        List<Map<String, Object>> issues = pageContent(list.body());
        assertEquals(threads, issues.size(), "All concurrent issues persisted");
        Set<String> distinct = new HashSet<>();
        for (var it : issues) {
            distinct.add((String) it.get("issueKey"));
        }
        assertEquals(threads, distinct.size(), "No duplicate issue keys under concurrency");
        assertTrue(distinct.stream().allMatch(k -> k.startsWith(key + "-")), "Keys keep the project prefix");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pageContent(String body) throws Exception {
        Map<String, Object> page = mapper.readValue(body, Map.class);
        return (List<Map<String, Object>>) page.get("content");
    }
}
