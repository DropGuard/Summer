package com.github.dropguard.summer.issuetracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.test.TestContainer;
import com.github.dropguard.summer.web.server.NettyServerRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared bootstrap for the Issue Tracker integration tests — PURE USER PERSPECTIVE.
 *
 * <p>
 * Follows the summer-twitter demo's convention exactly: starts a real Postgres
 * via Testcontainers, loads the schema from {@code docker/init/01-schema.sql},
 * points the framework at it through the public {@code com.github.dropguard.summer.test.datasource.url}
 * system property, and starts the Netty server on a random port. Only the public
 * test API ({@code Testing.buildForTest}) is used — no {@code com.github.dropguard.summer.test.internal}
 * type, because those are reserved for framework-owned tests.
 * </p>
 *
 * <p>
 * These tests assert the demo's OWN business behavior (RBAC rules, audit trail,
 * dynamic filtering). They intentionally do NOT assert Summer-internal contracts
 * such as dual-engine parity or negative-fixture isolation — that is the
 * framework's own tck's job, not this external demo's.
 * </p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Isolated
public abstract class AbstractIssueTrackerIT {

    // Per-class instance state: each IT subclass (TagIT, IssueFlowIT, RbacIT) owns
    // its own server + port, so concurrent IT classes never race on shared statics.
    // The Postgres @Container must stay static (Testcontainers requirement).
    protected BeanContainer context;
    private NettyServerRunner serverRunner;
    protected String baseUrl;
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    protected static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("issuetracker_test")
            .withUsername("test")
            .withPassword("test");

    @BeforeAll
    void startEnvironment() throws Exception {
        try (java.io.Writer w = new java.io.FileWriter("/tmp/probe-it.log", true)) {
            w.write("BEFOREALL ran\n");
        }
        String jdbcUrl = POSTGRES.getJdbcUrl();
        try (HikariDataSource admin = new HikariDataSource(new HikariConfig() {
            {
                setJdbcUrl(jdbcUrl);
                setUsername("test");
                setPassword("test");
                setDriverClassName("org.postgresql.Driver");
            }
        }); Connection conn = admin.getConnection()) {
            try (Statement reset = conn.createStatement()) {
                reset.execute("DROP SCHEMA IF EXISTS public CASCADE");
                reset.execute("CREATE SCHEMA public");
            }
            applySql(conn, "docker/init/01-schema.sql");
        }

        System.setProperty("com.github.dropguard.summer.test.datasource.url", jdbcUrl);
        System.setProperty("com.github.dropguard.summer.test.datasource.username", "test");
        System.setProperty("com.github.dropguard.summer.test.datasource.password", "test");

        // Runtime is the dev/escape-hatch engine; production uses AOT (selected by
        // SummerApplication/DiEngine in jar mode). The demo's ITs exercise the
        // runtime engine, which is the supported local-dev path.
        context = TestContainer.builder().testClass(AbstractIssueTrackerIT.class).mocks(
                java.util.List.of()).build();
        for (Object runner : context.getBeans(NettyServerRunner.class)) {
            serverRunner = (NettyServerRunner) runner;
            serverRunner.run(context);
        }
        int port = serverRunner.getPort();
        assertTrue(port > 0, "Netty must bind to an actual port");
        baseUrl = "http://localhost:" + port;
    }

    @AfterAll
    void stopEnvironment() throws Exception {
        try (java.io.Writer w = new java.io.FileWriter("/tmp/probe-it.log", true)) {
            w.write("AFTERALL ran; serverRunner=" + (serverRunner == null ? "NULL" : serverRunner.getClass().getSimpleName()) + "\n");
        }
        if (serverRunner != null) {
            serverRunner.stop();
            serverRunner = null;
        }
        // startEnvironment(), not here — each IT subclass still runs its own
        // @AfterAll, but we must not close the server they all share.
        System.clearProperty("com.github.dropguard.summer.test.datasource.url");
        System.clearProperty("com.github.dropguard.summer.test.datasource.username");
        System.clearProperty("com.github.dropguard.summer.test.datasource.password");
    }

    // ── HTTP helpers ───────────────────────────────────────────────────

    protected HttpResponse<String> get(String path) throws Exception {
        return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> authGet(String path, String token) throws Exception {
        return client.send(request(path).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(String path, String json, String token) throws Exception {
        var b = request(path).header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return client.send(b.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> put(String path, String json, String token) throws Exception {
        return client.send(request(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> delete(String path, String token) throws Exception {
        return client.send(request(path)
                .header("Authorization", "Bearer " + token)
                .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10));
    }

    protected record TokenAndUser(String token, Long userId, String username) {}

    protected TokenAndUser registerAndLogin(String username, String orgSlug) throws Exception {
        String unique = username + "_" + System.nanoTime() + "_" + (int) (Math.random() * 100000);
        String register = """
                {"username":"%s","displayName":"%s","email":"%s@test.com","password":"pw","orgName":"Org %s","orgSlug":"%s"}
                """.formatted(unique, username, username, orgSlug, orgSlug);
        HttpResponse<String> reg = post("/api/auth/register", register, null);
        if (reg.statusCode() != 201) {
            throw new IllegalStateException("Register failed: " + reg.statusCode() + " " + reg.body());
        }
        Map<?, ?> body = mapper.readValue(reg.body(), Map.class);
        return new TokenAndUser((String) body.get("token"), ((Number) body.get("userId")).longValue(), username);
    }

    private static void applySql(Connection conn, String relativePath) throws Exception {
        String sql = Files.readString(Path.of(relativePath));
        List<String> lines = new ArrayList<>();
        for (String line : sql.split("\n")) {
            int comment = line.indexOf("--");
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            lines.add(line);
        }
        try (Statement st = conn.createStatement()) {
            for (String stmt : String.join("\n", lines).split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
        }
    }
}
