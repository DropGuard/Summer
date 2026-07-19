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

import summer.core.BeanContainer;
import summer.test.TestRunContext;
import summer.test.devservices.TestcontainersDevServicesHolder;
import summer.web.server.NettyServerRunner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared bootstrap for the Twitter integration tests.
 *
 * <p>
 * Every {@code *IT} class inherits the same real-stack setup: one shared
 * Postgres + one shared Redis (started once per JVM via {@link TestRunContext}),
 * the schema and seed loaded <b>from the demo's own {@code docker/} SQL</b>
 * (single source of truth — no copy, so the IT database can never drift from the
 * demo database), and a Netty server bound to a random port. The shared
 * dev-services are owned by {@link TestRunContext}; this base class only points
 * the test configs at them and starts the application server.
 * </p>
 *
 * <p>
 * REST integration tests extend this and reuse the HTTP helpers; the WebSocket
 * integration test (MessageWSIT) extends it too but brings its own WS client.
 * </p>
 *
 * <p>
 * <b>Diagnosing a red IT.</b> The client only ever sees a generic
 * {@code "Internal Server Error"} (the framework deliberately does not leak
 * server internals to the response). The real root cause lives in the
 * <b>server-side log</b> - the framework logs it via {@code log.error}
 * (e.g. {@code HttpContext.error} for HTTP 500s, and
 * {@code WebSocketUpgradeHandler} for WS upgrade failures).
 * </p>
 * <p>
 * <b>Where the log lands</b> (see {@code logback-test.xml} on the test
 * classpath, which overrides the app's {@code logback.xml}): every server
 * ERROR/WARN is written to {@code target/it-server.log} - a deterministic sink
 * that does NOT depend on surefire forking a new JVM. To triage a red IT:
 * {@code grep -nE 'ERROR|PSQLException|WebSocket upgrade failed' summer-twitter/target/it-server.log}.
 * The console also shows INFO-level output for the usual green-bar feedback.
 * Surefire/failsafe's own {@code output-<class>.txt} (under
 * {@code target/surefire-reports/}, only when {@code redirectTestOutputToFile}
 * is on AND the run forks) is a CI-only fallback, not the local primary.
 * Do NOT work around a red IT by echoing the exception into the HTTP response
 * body - that would leak internals; read {@code target/it-server.log} instead.
 * </p>
 */
abstract class AbstractTwitterIT {

	protected static BeanContainer context;
	protected static String baseUrl;
	protected static final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5)).build();
	protected static final ObjectMapper mapper = new ObjectMapper();

	@BeforeAll
	static void startEnvironment() throws Exception {
		TestRunContext ctx = TestRunContext.instance();
		ctx.ensureDevServices(java.util.Map.of("database", "twitter_test"));
		TestcontainersDevServicesHolder holder = (TestcontainersDevServicesHolder) ctx.devServices();

		try (Connection conn = holder.openAdminConnection()) {
			// The shared Postgres is JVM-wide, so every *IT class reuses the same
			// database. Reset it to a clean schema before (re)loading, so each IT
			// starts from the demo's seed data regardless of run order.
			try (Statement reset = conn.createStatement()) {
				reset.execute("DROP SCHEMA IF EXISTS public CASCADE");
				reset.execute("CREATE SCHEMA public");
			}
			applySql(conn, "docker/init/01-schema.sql");
			applySql(conn, "docker/seed.sql");
		}

		System.setProperty("summer.test.datasource.url", "summer:devservices:postgres");
		System.setProperty("summer.redis.uri", holder.redisUri());

		context = summer.test.Testing.buildForTest(AbstractTwitterIT.class);

		for (Object runner : context.getBeans(NettyServerRunner.class)) {
			((NettyServerRunner) runner).run(context);
		}
		int port = NettyServerRunner.getActualPort();
		assertTrue(port > 0, "Netty must bind to an actual port");
		baseUrl = "http://localhost:" + port;
	}

	@AfterAll
	static void stopEnvironment() {
		System.clearProperty("summer.test.datasource.url");
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

