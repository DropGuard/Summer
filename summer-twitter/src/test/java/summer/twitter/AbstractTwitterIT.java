package summer.twitter;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import summer.core.BeanContainer;
import summer.test.Testing;
import summer.web.server.NettyServerRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared bootstrap for the Twitter integration tests — PURE USER PERSPECTIVE.
 *
 * <p>
 * This demo uses only the <b>public</b> test API: it starts Testcontainers
 * directly (exactly like {@code RedisIntegrationIT}) and points the framework
 * at the real database via the {@code summer.test.datasource.url} /
 * {@code summer.redis.uri} system properties. It does NOT use any
 * {@code summer.test.internal} type — that is reserved for framework-owned
 * integration tests (see {@code summer-integration-test}), not for demos.
 * </p>
 *
 * <p>
 * Every {@code *IT} class inherits the same real-stack setup: one shared
 * Postgres + one shared Redis (started once per JVM via the {@code static}
 * {@code @Container} fields), the schema and seed loaded from the demo's own
 * {@code docker/} SQL (single source of truth), and a Netty server on a random
 * port. The shared dev-services are owned by this base class; subclasses only
 * point their configs at them and start the application server.
 * </p>
 *
 * <p>
 * <b>Diagnosing a red IT.</b> The client only ever sees a generic
 * {@code "Internal Server Error"}. The real root cause lives in the server-side
 * log. See {@code logback-test.xml} on the test classpath; every server
 * ERROR/WARN is written to {@code target/it-server.log}. To triage:
 * {@code grep -nE 'ERROR|PSQLException|WebSocket upgrade failed' summer-twitter/target/it-server.log}.
 * </p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Isolated
abstract class AbstractTwitterIT {

	// Per-class instance state: each IT subclass owns its own server + port, so
	// concurrent IT classes never race on shared statics. Postgres/Redis
	// @Container fields stay static (Testcontainers requirement).
	protected BeanContainer context;
	protected String baseUrl;
	private NettyServerRunner serverRunner;
	protected static final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5)).build();
	protected static final ObjectMapper mapper = new ObjectMapper();

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
			.withDatabaseName("twitter_test")
			.withUsername("test")
			.withPassword("test");

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7"))
			.withExposedPorts(6379);

	@BeforeAll
	void startEnvironment() throws Exception {
		String jdbcUrl = POSTGRES.getJdbcUrl();
		HikariDataSource adminDs = new HikariDataSource(new HikariConfig() {
			{
				setJdbcUrl(jdbcUrl);
				setUsername("test");
				setPassword("test");
				setDriverClassName("org.postgresql.Driver");
			}
		});
		try (Connection conn = adminDs.getConnection()) {
			try (Statement reset = conn.createStatement()) {
				reset.execute("DROP SCHEMA IF EXISTS public CASCADE");
				reset.execute("CREATE SCHEMA public");
			}
			applySql(conn, "docker/init/01-schema.sql");
			applySql(conn, "docker/seed.sql");
		} finally {
			adminDs.close();
		}

		System.setProperty("summer.test.datasource.url", jdbcUrl);
		System.setProperty("summer.test.datasource.username", "test");
		System.setProperty("summer.test.datasource.password", "test");
		System.setProperty("summer.redis.uri",
				"redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));

		context = Testing.buildForTest(AbstractTwitterIT.class);

		for (Object runner : context.getBeans(NettyServerRunner.class)) {
			serverRunner = (NettyServerRunner) runner;
			serverRunner.run(context);
		}
		int port = serverRunner.getPort();
		assertTrue(port > 0, "Netty must bind to an actual port");
		baseUrl = "http://localhost:" + port;
	}

	@AfterAll
	void stopEnvironment() {
		if (serverRunner != null) {
			try {
				serverRunner.stop();
			} catch (Exception e) {
				// best-effort teardown
			}
			serverRunner = null;
		}
		System.clearProperty("summer.test.datasource.url");
		System.clearProperty("summer.test.datasource.username");
		System.clearProperty("summer.test.datasource.password");
		System.clearProperty("summer.redis.uri");
		context = null;
	}

	// ── Shared HTTP helpers ──────────────────────────────────────────────

	protected HttpResponse<String> get(String path) throws Exception {
		return client.send(
				HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	protected HttpResponse<String> authGet(String path, String token) throws Exception {
		return client.send(
				HttpRequest.newBuilder()
						.uri(URI.create(baseUrl + path))
						.header("Authorization", "Bearer " + token)
						.GET()
						.build(),
				HttpResponse.BodyHandlers.ofString());
	}

	protected HttpResponse<String> post(String path, String json) throws Exception {
		return client.send(
				HttpRequest.newBuilder()
						.uri(URI.create(baseUrl + path))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(json))
						.build(),
				HttpResponse.BodyHandlers.ofString());
	}

	protected HttpResponse<String> post(String path, String json, String token) throws Exception {
		return client.send(
				HttpRequest.newBuilder()
						.uri(URI.create(baseUrl + path))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer " + token)
						.POST(HttpRequest.BodyPublishers.ofString(json))
						.build(),
				HttpResponse.BodyHandlers.ofString());
	}

	protected HttpResponse<String> put(String path, String token, String json) throws Exception {
		return client.send(
				HttpRequest.newBuilder()
						.uri(URI.create(baseUrl + path))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer " + token)
						.PUT(HttpRequest.BodyPublishers.ofString(json))
						.build(),
				HttpResponse.BodyHandlers.ofString());
	}

	protected record TokenAndUser(String token, String username) {}

	protected TokenAndUser registerAndGetToken(String username, String password) throws Exception {
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
		return new TokenAndUser((String) body.get("token"), username);
	}

	// ── SQL loader ───────────────────────────────────────────────────────

	/** Executes a SQL file: split on ';', drop '--' comments, skip blanks. */
	private static void applySql(Connection conn, String relativePath) throws Exception {
		String sql = Files.readString(Path.of(relativePath));
		List<String> statements = new ArrayList<>();
		for (String line : sql.split("\n")) {
			int comment = line.indexOf("--");
			if (comment >= 0) {
				line = line.substring(0, comment);
			}
			statements.add(line);
		}
		String joined = String.join("\n", statements);
		try (Statement st = conn.createStatement()) {
			for (String stmt : joined.split(";")) {
				String trimmed = stmt.trim();
				if (!trimmed.isEmpty()) {
					st.execute(trimmed);
				}
			}
		}
	}
}
