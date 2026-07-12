package summer.twitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import summer.core.BeanContainer;
import summer.runtime.RuntimeBeanContainerBuilder;
import summer.twitter.auth.AuthController;
import summer.twitter.auth.AuthMiddleware;
import summer.twitter.config.TestDatabaseConfig;
import summer.twitter.config.WebSocketConfig;
import summer.twitter.social.FollowController;
import summer.twitter.social.LikeController;
import summer.twitter.timeline.TimelineController;
import summer.twitter.tweet.TweetController;
import summer.twitter.user.UserController;
import summer.web.GlobalMiddlewareChain;
import summer.web.WebInfrastructureConfiguration;
import summer.web.server.NettyServerConfiguration;
import summer.web.server.NettyServerRunner;
import summer.web.server.RouterConfiguration;
import summer.runtime.HttpParameterResolverConfiguration;
import summer.runtime.RuntimeWebConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static summer.twitter.support.TwitterTestDatabase.*;

/**
 * End-to-end integration test for the Summer Twitter application.
 *
 * <p>Architecture:
 * <ol>
 *   <li>Disposable PostgreSQL container (Testcontainers 1.21.4)</li>
 *   <li>{@link TestDatabaseConfig} replaces production {@code DatabaseConfig}
 *       via {@code @Replaces}, reads dynamic container URL from system properties</li>
 *   <li>Seeds-based container build via {@code RuntimeBeanContainerBuilder}
 *       — only beans reachable from seeds are instantiated</li>
 *   <li>Netty bound to random port (test application.yml sets server.port: 0)</li>
 *   <li>Auth middleware applied globally with public-route exemptions</li>
 * </ol>
 * </p>
 */
class TwitterApplicationIT {

    private static PostgreSQLContainer<?> postgres;
    private static BeanContainer context;
    private static String baseUrl;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void startEnvironment() throws Exception {
        // 1. Start disposable PostgreSQL
        postgres = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("twitter_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        // 2. Initialize schema + seed data
        try (var conn = connect()) {
            initSchema(conn);
            resetToSeed(conn);
        }

        // 3. Set system properties for TestDatabaseConfig
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/twitter_test";
        System.setProperty("summer.test.datasource.url", jdbcUrl);
        System.setProperty("summer.test.datasource.username", "test");
        System.setProperty("summer.test.datasource.password", "test");

        // 4. Build the bean container from seeds + external GlobalMiddlewareChain
        GlobalMiddlewareChain chain = new GlobalMiddlewareChain(List.of(AuthMiddleware.class));
        context = RuntimeBeanContainerBuilder.buildFromSeedsWithExternal(
                new Class<?>[]{
                        TestDatabaseConfig.class,
                        NettyServerConfiguration.class,
                        RouterConfiguration.class,
                        RuntimeWebConfiguration.class,
                        HttpParameterResolverConfiguration.class,
                        WebInfrastructureConfiguration.class,
                        WebSocketConfig.class,
                        // Controllers + middleware (explicit seeds because
                        // RuntimeRouteRegistrar discovers them from the Jandex
                        // index at runtime, not from the DI closure)
                        AuthMiddleware.class,
                        AuthController.class,
                        UserController.class,
                        TweetController.class,
                        FollowController.class,
                        LikeController.class,
                        TimelineController.class
                },
                chain    // external bean: GlobalMiddlewareChain
        );

        // 5. Start ApplicationRunners (starts Netty)
        for (Object runner : context.getBeans(NettyServerRunner.class)) {
            ((NettyServerRunner) runner).run(context);
        }

        int port = NettyServerRunner.getActualPort();
        assertTrue(port > 0, "Netty must bind to an actual port");
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    static void stopEnvironment() throws Exception {
        System.clearProperty("summer.test.datasource.url");
        System.clearProperty("summer.test.datasource.username");
        System.clearProperty("summer.test.datasource.password");
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    private static java.sql.Connection connect() throws Exception {
        return java.sql.DriverManager.getConnection(
                "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/twitter_test",
                "test", "test");
    }

    // ── Test helpers ────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authGet(String path, String token) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String token, String json) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String registerAndGetToken(String username, String password) throws Exception {
        String registerJson = """
                {"username":"%s","displayName":"IT User","email":"%s@test.com","password":"%s"}
                """.formatted(username, username, password);
        HttpResponse<String> reg = post("/api/auth/register", registerJson);
        assertEquals(201, reg.statusCode(), "Registration should succeed");

        String loginJson = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        HttpResponse<String> log = post("/api/auth/login", loginJson);
        assertEquals(200, log.statusCode(), "Login should succeed");

        Map<?, ?> body = mapper.readValue(log.body(), Map.class);
        return (String) body.get("token");
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    void registerAndLoginFlow() throws Exception {
        String user = "it_flow_" + System.nanoTime();
        String token = registerAndGetToken(user, "pass123");
        assertNotNull(token, "JWT token must not be null");
        assertFalse(token.isBlank(), "JWT token must not be blank");
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String user = "it_wrong_" + System.nanoTime();
        registerAndGetToken(user, "correct");

        HttpResponse<String> res = post("/api/auth/login", """
                {"username":"%s","password":"wrong"}
                """.formatted(user));
        assertEquals(401, res.statusCode(), "Wrong password should return 401");
    }

    @Test
    void healthEndpointsArePublic() throws Exception {
        assertEquals(200, get("/health/live").statusCode());
        assertEquals(200, get("/health/ready").statusCode());
    }

    @Test
    void protectedRouteReturns401() throws Exception {
        assertEquals(401, get("/api/users/me").statusCode());

        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/users/me"))
                        .header("Authorization", "Bearer invalid.token.here")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "Invalid token should return 401");
    }

    @Test
    void authenticatedProfileFlow() throws Exception {
        String token = registerAndGetToken("prof_" + System.nanoTime(), "pw");

        HttpResponse<String> updateRes = put("/api/users/me", token, """
                {"displayName":"Updated Name","bio":"Integration test bio"}
                """);
        assertEquals(200, updateRes.statusCode(), "Profile update should succeed");

        Map<?, ?> profile = mapper.readValue(updateRes.body(), Map.class);
        assertEquals("Updated Name", profile.get("displayName"));
        assertEquals("Integration test bio", profile.get("bio"));
        assertFalse(profile.containsKey("email"), "Must not expose email");
        assertFalse(profile.containsKey("passwordHash"), "Must not expose password hash");
        assertNotNull(profile.get("createdAt"), "Must include created_at timestamp");
    }

    @Test
    void userProfileRead() throws Exception {
        String token = registerAndGetToken("reader_" + System.nanoTime(), "pw");

        HttpResponse<String> res = authGet("/api/users/profile_tester", token);
        assertEquals(404, res.statusCode(), "Non-existent user should return 404");
    }

    @Test
    void tweetCreateAndRead() throws Exception {
        String token = registerAndGetToken("tweet_" + System.nanoTime(), "pw");

        HttpResponse<String> createRes = post("/api/tweets", """
                {"content":"Hello from integration test"}
                """, token);
        assertEquals(201, createRes.statusCode(), "Tweet creation should succeed");

        String body = createRes.body().strip();
        assertTrue(body.startsWith("{") || body.matches("\\d+"),
                "Create response should be JSON or numeric ID, got: " + body);
        long tweetId = body.startsWith("{")
                ? ((Number) mapper.readValue(body, Map.class).get("id")).longValue()
                : Long.parseLong(body);

        HttpResponse<String> getRes = authGet("/api/tweets/" + tweetId, token);
        assertEquals(200, getRes.statusCode(), "Should retrieve the created tweet");

        Map<?, ?> tweet = mapper.readValue(getRes.body(), Map.class);
        assertEquals("Hello from integration test", tweet.get("content"));
    }

    @Test
    void tweetReplyFlow() throws Exception {
        String token = registerAndGetToken("reply_" + System.nanoTime(), "pw");

        HttpResponse<String> createRes = post("/api/tweets", """
                {"content":"Parent tweet"}
                """, token);
        assertEquals(201, createRes.statusCode());
        String createBody = createRes.body().strip();
        long tweetId = createBody.startsWith("{")
                ? ((Number) mapper.readValue(createBody, Map.class).get("id")).longValue()
                : Long.parseLong(createBody);

        HttpResponse<String> replyRes = post("/api/tweets", """
                {"content":"Reply!","parentId":%d}
                """.formatted(tweetId), token);
        assertEquals(201, replyRes.statusCode(), "Reply should succeed");
    }

    // ── Convenience: authPost ──────────────────────────────────────────

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
