package summer.twitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Integration coverage for the tweet write/read surface — the path that
 * previously 500'd because the timeline fan-out touches Redis. These tests run
 * against the real Postgres + real Redis dev-services, so they exercise the
 * full create → fan-out → persist chain that the pure unit tests mock away.
 */
class TweetIT extends AbstractTwitterIT {

    @Test
    void tweetCreateAndRead() throws Exception {
        String token = registerAndGetToken("tweet_" + System.nanoTime(), "pw").token();

        var createRes = post("/api/tweets", """
                {"content":"Hello from integration test"}
                """, token);
        assertEquals(201, createRes.statusCode(), "Tweet creation should succeed");

        String body = createRes.body().strip();
        long tweetId = body.startsWith("{")
                ? ((Number) mapper.readValue(body, Map.class).get("id")).longValue()
                : Long.parseLong(body);

        var getRes = authGet("/api/tweets/" + tweetId, token);
        assertEquals(200, getRes.statusCode(), "Should retrieve the created tweet");

        Map<?, ?> tweet = mapper.readValue(getRes.body(), Map.class);
        assertEquals("Hello from integration test", tweet.get("content"));
    }

    @Test
    void tweetReplyFlow() throws Exception {
        String token = registerAndGetToken("reply_" + System.nanoTime(), "pw").token();

        var createRes = post("/api/tweets", """
                {"content":"Parent tweet"}
                """, token);
        assertEquals(201, createRes.statusCode());
        String createBody = createRes.body().strip();
        long tweetId = createBody.startsWith("{")
                ? ((Number) mapper.readValue(createBody, Map.class).get("id")).longValue()
                : Long.parseLong(createBody);

        var replyRes = post("/api/tweets", """
                {"content":"Reply!","parentId":%d}
                """.formatted(tweetId), token);
        assertEquals(201, replyRes.statusCode(), "Reply should succeed");

        Map<?, ?> reply = mapper.readValue(replyRes.body(), Map.class);
        assertEquals(tweetId, ((Number) reply.get("parentId")).longValue(), "Reply must point at its parent");
    }

    @Test
    void retweetFansOutAndIncrementsCount() throws Exception {
        String token = registerAndGetToken("retweet_" + System.nanoTime(), "pw").token();
        long originalId = createTweet(token, "Original to be retweeted");

        var retweetRes = post("/api/tweets/" + originalId + "/retweet", "", token);
        assertEquals(201, retweetRes.statusCode(), "Retweet should succeed");
        Map<?, ?> retweet = mapper.readValue(retweetRes.body(), Map.class);
        assertEquals("RETWEET", retweet.get("type"));

        // The original's retweet count must have been incremented by the service.
        var original = authGet("/api/tweets/" + originalId, token);
        Map<?, ?> orig = mapper.readValue(original.body(), Map.class);
        assertEquals(1, ((Number) orig.get("retweetCount")).intValue());
    }

    @Test
    void createRequiresAuth() throws Exception {
        var res = post("/api/tweets", """
                {"content":"no auth"}
                """);
        assertEquals(401, res.statusCode(), "Creating a tweet requires authentication");
    }

    @Test
    void retweetMissingOriginalReturns404() throws Exception {
        String token = registerAndGetToken("retweet_missing_" + System.nanoTime(), "pw").token();
        var res = post("/api/tweets/99999999/retweet", "", token);
        assertEquals(404, res.statusCode(), "Retweeting a missing tweet should 404");
    }

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
}
