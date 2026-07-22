package summer.it;

import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import summer.core.BeanContainer;
import summer.test.devservices.TestcontainersDevServicesHolder;
import summer.test.internal.TestRunContext;

/**
 * Shared bootstrap for the framework's real-stack integration tests.
 *
 * <p>
 * Uses the <b>internal</b> {@code DevServicesHolder} convenience (via
 * {@code TestRunContext}) to start one shared Postgres + Redis for the JVM and
 * point the test config at it. This is framework-internal — demos must not
 * depend on {@code summer.test.internal}; they start Testcontainers directly
 * and set {@code summer.test.datasource.url} / {@code summer.redis.uri}
 * themselves (see {@code RedisIntegrationIT}).
 * </p>
 */
abstract class AbstractFrameworkIT {

	protected static BeanContainer context;

	@BeforeAll
	static void startEnvironment() throws Exception {
		TestRunContext ctx = TestRunContext.instance();
		ctx.ensureDevServices(java.util.Map.of("database", "summer_it"));
		TestcontainersDevServicesHolder holder = (TestcontainersDevServicesHolder) ctx.devServices();

		try (Connection conn = holder.openAdminConnection()) {
			try (Statement st = conn.createStatement()) {
				st.execute("DROP TABLE IF EXISTS greetings");
				st.execute("CREATE TABLE greetings (id BIGINT PRIMARY KEY, text VARCHAR(255))");
			}
		}

		System.setProperty("summer.test.datasource.url", "summer:devservices:postgres");
		System.setProperty("summer.redis.uri", holder.redisUri());

		context = summer.test.Testing.buildForTest(AbstractFrameworkIT.class);
	}

	@AfterAll
	static void stopEnvironment() {
		System.clearProperty("summer.test.datasource.url");
		System.clearProperty("summer.redis.uri");
		context = null;
	}
}
