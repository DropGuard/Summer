package com.github.dropguard.summer.twitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Integration coverage for the timeline read path against the <b>real Redis</b>
 * dev-service — the part the pure unit tests mock away. Verifies fan-out landing
 * in a follower's timeline (zset write + read), the merge-and-ordering path, and
 * the cursor-stale fallback. This is the integration-layer blind spot that only
 * a real Redis exercises.
 */
class TimelineIT extends AbstractTwitterIT {

    @Test
    void fanOutLandsInFollowersTimeline() throws Exception {
        // follower F follows author B; when B tweets, the virtual-thread fan-out
        // must land B's tweet in F's real-Redis timeline.
        TokenAndUser follower = registerAndGetToken("tl_follower_" + System.nanoTime(), "pw");
        TokenAndUser author = registerAndGetToken("tl_author_" + System.nanoTime(), "pw");
        post("/api/users/" + author.username() + "/follow", "", follower.token());

        long tweetId = createTweet(author.token(), "timeline integration tweet");

        List<Map<?, ?>> timeline = pollTimeline(follower.token());
        assertTrue(timeline.stream().anyMatch(t -> ((Number) t.get("id")).longValue() == tweetId),
                "Follower's timeline should contain the tweet fanned out by the author");
    }

    @Test
    void timelineOrdersByIdDescendingOnEqualScore() throws Exception {
        // Follower C follows two authors who each post; with equal (zero) scores the
        // merge must order by id descending (newest first).
        TokenAndUser follower = registerAndGetToken("tl_c_" + System.nanoTime(), "pw");
        TokenAndUser b1 = registerAndGetToken("tl_b1_" + System.nanoTime(), "pw");
        TokenAndUser b2 = registerAndGetToken("tl_b2_" + System.nanoTime(), "pw");
        post("/api/users/" + b1.username() + "/follow", "", follower.token());
        post("/api/users/" + b2.username() + "/follow", "", follower.token());

        createTweet(b1.token(), "first");
        createTweet(b2.token(), "second");
        createTweet(b1.token(), "third");

        var res = authGet("/api/timeline", follower.token());
        assertEquals(200, res.statusCode());
        List<Map<?, ?>> timeline = mapper.readValue(res.body(), List.class);
        assertEquals(3, timeline.size(), "All three fanned-out tweets must appear");

        for (int i = 1; i < timeline.size(); i++) {
            long prev = ((Number) timeline.get(i - 1).get("id")).longValue();
            long cur = ((Number) timeline.get(i).get("id")).longValue();
            assertTrue(prev >= cur, "Timeline must be ordered by descending id, got " + timeline);
        }
    }

    @Test
    void cursorStaleFallsBackToHead() throws Exception {
        // A cursor id absent from the merged set must fall back to the feed head
        // rather than an empty page (deleted-tweet tolerance).
        TokenAndUser follower = registerAndGetToken("tl_cursor_" + System.nanoTime(), "pw");
        TokenAndUser author = registerAndGetToken("tl_cur_author_" + System.nanoTime(), "pw");
        post("/api/users/" + author.username() + "/follow", "", follower.token());
        createTweet(author.token(), "cursor fallback tweet");

        var res = authGet("/api/timeline?cursor=999999999", follower.token());
        assertEquals(200, res.statusCode());
        List<Map<?, ?>> timeline = mapper.readValue(res.body(), List.class);
        assertFalse(timeline.isEmpty(), "Stale cursor must fall back to feed head, not an empty page");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private long createTweet(String token, String content) throws Exception {
        var res = post("/api/tweets", """
                {"content":"%s"}
                """.formatted(content), token);
        assertEquals(201, res.statusCode());
        String body = res.body().strip();
        return body.startsWith("{")
                ? ((Number) mapper.readValue(body, Map.class).get("id")).longValue()
                : Long.parseLong(body);
    }

    @SuppressWarnings("unchecked")
    private List<Map<?, ?>> pollTimeline(String token) throws Exception {
        for (int i = 0; i < 50; i++) {
            var res = authGet("/api/timeline", token);
            if (res.statusCode() == 200) {
                List<Map<?, ?>> tl = mapper.readValue(res.body(), List.class);
                if (!tl.isEmpty()) {
                    return tl;
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return List.of();
    }
}
