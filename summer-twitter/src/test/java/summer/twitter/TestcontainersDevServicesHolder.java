package summer.test.devservices;

import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;

import summer.test.DevServicesHolder;

/**
 * Testcontainers-backed dev-services for the Summer Twitter integration tests.
 *
 * <p>
 * This is the {@code summer-test} framework's well-known holder implementation
 * (located by {@code TestRunContext} via reflection), kept in the twitter module
 * so {@code summer-test} itself never depends on Testcontainers or Docker. The
 * holder starts the real infrastructure the twitter app needs — a single Postgres
 * and a single Redis — for the whole test run, and hands out one shared
 * {@link DataSource} pool and one Redis URI. That eliminates the
 * per-{@code @SummerTest} pool proliferation that previously exhausted Postgres'
 * {@code max_connections}, and gives the integration test a faithful copy of the
 * production stack (JDBC + Redis) rather than a partial one.
 * </p>
 *
 * <p>
 * Postgres connection coordinates are written as a {@link ConnectionDescriptor}
 * that the test's {@code @Replaces} {@code TestDatabaseConfig} reads, so
 * production's {@code DatabaseConfig} remains the single source of truth and is
 * never touched. Redis is wired through the app's existing
 * {@code summer.redis.uri} system property (the production
 * {@code RedisAutoConfiguration} already falls back to it), so no production
 * code changes either.
 * </p>
 */
public class TestcontainersDevServicesHolder implements DevServicesHolder {

	/** URL form the test DB config recognizes as "use the shared dev-services pool". */
	private static final String SHARED_URL_SCHEME = "summer:devservices:postgres";

	private PostgreSQLContainer<?> postgres;
	private GenericContainer<?> redis;
	private HikariDataSource sharedPool;
	private volatile boolean started;

	@Override
	public synchronized ConnectionDescriptor start(Map<String, String> requestedEnvironment) {
		if (started) {
			return currentDescriptor();
		}
		String dbName = requestedEnvironment.getOrDefault("database", "twitter_test");
		postgres = new PostgreSQLContainer<>("postgres:15")
				.withDatabaseName(dbName)
				.withUsername("test")
				.withPassword("test");
		postgres.start();

		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(postgres.getJdbcUrl());
		config.setUsername("test");
		config.setPassword("test");
		config.setDriverClassName("org.postgresql.Driver");
		// One pool for the entire JVM. Every @SummerTest that wants the real DB reuses
		// it; we deliberately keep the size small and rely on the shared lifetime.
		config.setMaximumPoolSize(4);
		sharedPool = new HikariDataSource(config);

		redis = new GenericContainer<>("redis:7").withExposedPorts(6379);
		redis.start();

		started = true;
		return currentDescriptor();
	}

	@Override
	public synchronized void stop() {
		if (sharedPool != null) {
			try {
				sharedPool.close();
			} catch (Exception ignored) {
			}
			sharedPool = null;
		}
		if (postgres != null) {
			try {
				postgres.stop();
			} catch (Exception ignored) {
			}
			postgres = null;
		}
		if (redis != null) {
			try {
				redis.stop();
			} catch (Exception ignored) {
			}
			redis = null;
		}
		started = false;
	}

	@Override
	public boolean owns(String url) {
		return url != null && url.startsWith(SHARED_URL_SCHEME);
	}

	@Override
	public DataSource sharedDataSource(String url) {
		if (!owns(url)) {
			throw new IllegalArgumentException("URL not owned by dev-services: " + url);
		}
		if (sharedPool == null) {
			throw new IllegalStateException("Dev-services pool requested before start()");
		}
		return sharedPool;
	}

	private ConnectionDescriptor currentDescriptor() {
		// The descriptor URL is the shared scheme; the real JDBC coordinates live
		// inside sharedDataSource(). The test DB config routes through owns()+that.
		return new ConnectionDescriptor(SHARED_URL_SCHEME, "test", "test", "org.postgresql.Driver");
	}

	/** Redis URI for the shared container (redis://host:port), for {@code summer.redis.uri}. */
	public String redisUri() {
		if (redis == null) {
			throw new IllegalStateException("Dev-services not started");
		}
		return "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
	}

	/** Convenience the integration test can use to run schema/seed DDL. */
	public Connection openAdminConnection() throws Exception {
		if (postgres == null) {
			throw new IllegalStateException("Dev-services not started");
		}
		return postgres.createConnection("");
	}
}
