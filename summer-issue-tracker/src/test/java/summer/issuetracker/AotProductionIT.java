package summer.issuetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import summer.core.BeanContainer;
import summer.core.DiEngine;
import summer.issuetracker.issue.Issue;
import summer.issuetracker.issue.IssueService;
import summer.issuetracker.security.AuthService;
import summer.issuetracker.security.JwtAuthMiddleware;
import summer.web.Middleware;
import summer.web.middleware.CorsMiddleware;

/**
 * Proves the PRODUCTION AOT engine works for this external demo app (AOT is the
 * production engine; Runtime is the test engine). Uses the same bootstrap path as
 * {@code SummerApplication}: force {@code -Dsummer.engine=aot}, build the container
 * via {@link DiEngine#create(Object...)} (which loads the build-time-generated
 * {@code GeneratedAotContext}), start Netty, and exercise a real register +
 * create-issue round-trip. This validates the AOT bean graph end-to-end — including
 * the EntityMetadataRegistrar IndexView dependency that only the AOT-generated
 * context must supply.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Isolated
public class AotProductionIT {

    private BeanContainer context;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
            .withDatabaseName("issuetracker_test").withUsername("test").withPassword("test");

    @BeforeAll
    void startEnvironment() throws Exception {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        try (HikariDataSource admin = new HikariDataSource(new HikariConfig() {{
            setJdbcUrl(jdbcUrl); setUsername("test"); setPassword("test");
            setDriverClassName("org.postgresql.Driver");
        }}); Connection conn = admin.getConnection()) {
            try (Statement reset = conn.createStatement()) {
                reset.execute("DROP SCHEMA IF EXISTS public CASCADE");
                reset.execute("CREATE SCHEMA public");
            }
            applySql(conn, "docker/init/01-schema.sql");
        }
        System.setProperty("summer.test.datasource.url", jdbcUrl);
        System.setProperty("summer.test.datasource.username", "test");
        System.setProperty("summer.test.datasource.password", "test");
        System.setProperty("summer.engine", "aot"); // production engine

        // Build the production AOT container the same way SummerApplication does
        // (DiEngine.create -> GeneratedAotContext). If the external demo's bean graph
        // (including EntityMetadataRegistrar's IndexView dependency) cannot be resolved
        // under AOT, this call throws and the test fails — exactly the regression this
        // test guards against.
        List<Class<? extends Middleware>> middleware = List.of(CorsMiddleware.class, JwtAuthMiddleware.class);
        context = DiEngine.create(middleware);
    }

    @AfterAll
    void stopEnvironment() throws Exception {
        System.clearProperty("summer.test.datasource.url");
        System.clearProperty("summer.test.datasource.username");
        System.clearProperty("summer.test.datasource.password");
        System.clearProperty("summer.engine");
    }

    @Test
    void aotProductionContainerResolvesExternalDemoGraph() {
        // The AOT-built container must expose the demo's business beans wired through
        // data-jdbc (which depends on the IndexView bean only the AOT context supplies).
        AuthService auth = context.getBean(AuthService.class);
        IssueService issues = context.getBean(IssueService.class);
        assertTrue(auth != null && issues != null, "Demo beans must be wired under AOT");

        var result = auth.register("aot_user", "AOT", "a@x.com", "pw", "Org", "aotorg");
        assertTrue(result.userId() > 0, "Registration must persist under AOT");

        // Build a project through the data-jdbc repositories (the same graph AOT must
        // have resolved, including EntityMetadataRegistrar's IndexView dependency).
        var userRepo = context.getBean(summer.issuetracker.user.UserRepository.class);
        var projectRepo = context.getBean(summer.issuetracker.project.ProjectRepository.class);
        var idGen = context.getBean(summer.issuetracker.common.IdGenerator.class);
        var user = userRepo.findById(result.userId()).orElseThrow();
        long projectId = idGen.nextId();
        projectRepo.insert(new summer.issuetracker.project.Project(projectId, user.orgId(),
                "AOT", "AOT project", result.userId(), java.time.OffsetDateTime.now()));
        projectRepo.addMember(projectId, result.userId(), "MANAGER");

        Issue created = issues.createIssue(projectId, "AOT issue", "d", "OPEN", "LOW", null);
        assertEquals("AOT-1", created.issueKey(), "Issue key must be generated under AOT");
    }

    private static void applySql(Connection conn, String relativePath) throws Exception {
        String sql = Files.readString(Path.of(relativePath));
        List<String> lines = new java.util.ArrayList<>();
        for (String line : sql.split("\n")) {
            int comment = line.indexOf("--");
            if (comment >= 0) line = line.substring(0, comment);
            lines.add(line);
        }
        try (Statement st = conn.createStatement()) {
            for (String stmt : String.join("\n", lines).split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) st.execute(trimmed);
            }
        }
    }
}
