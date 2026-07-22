package summer.test.internal;
import java.util.Map;
import javax.sql.DataSource;
import summer.core.Internal;

/**
 * Supplies shared external resources (a real database, a message broker, …) for
 * the {@code @SummerTest} universe.
 *
 * <p>
 * A holder is the <b>subject</b> of the shared test-run lifecycle, not a field
 * hanging off it: the whole point of the static {@link SummerTestLifecycle} is
 * to start heavy resources at most once per JVM and tear them down only at JVM
 * exit. That lifetime is what eliminates the per-class connection-pool leak a
 * naive per-test universe caused.
 * </p>
 *
 * <p>
 * Summer deliberately does <em>not</em> copy Quarkus' config-level dev-services
 * injection. The application's {@code DatabaseConfig} is a hand-written
 * {@code @Configuration} class — the single source of truth. A holder writes a
 * connection descriptor into the shared context; the test's own
 * {@code @Replaces} config (e.g. {@code TestDatabaseConfig}) reads that
 * descriptor and builds the {@code DataSource}. Production code stays untouched
 * and the test environment keeps its own visible truth.
 * </p>
 *
 * <p>
 * Implementations are discovered reflectively by name so {@code summer-test}
 * itself carries no Testcontainers / Docker dependency — only modules that
 * actually run integration tests need those on their test classpath.
 * </p>
 */
@Internal
public interface DevServicesHolder {

	/**
	 * Called once when the shared context first decides a test universe needs a
	 * real external resource. Implementations should start the container (if any)
	 * and return a descriptor of how to connect.
	 *
	 * @param requestedEnvironment
	 *            hints such as a requested DB name; may be ignored
	 * @return a connection descriptor the test {@code @Replaces} config understands
	 */
	ConnectionDescriptor start(Map<String, String> requestedEnvironment);

	/**
	 * Tears down the resource. Called at most once, on JVM shutdown or when the
	 * context is reset. Must be idempotent.
	 */
	void stop();

	/**
	 * A descriptor written into the shared context and consumed by the test's
	 * {@code @Replaces} database config. Opaque to the framework — only the config
	 * that produced the holder understands its fields. We expose a single
	 * connection URL plus credentials because every SQL backend consumes exactly
	 * those four strings.
	 */
	final class ConnectionDescriptor {

		private final String url;
		private final String username;
		private final String password;
		private final String driverClassName;

		public ConnectionDescriptor(String url, String username, String password, String driverClassName) {
			this.url = url;
			this.username = username;
			this.password = password;
			this.driverClassName = driverClassName;
		}

		public String url() {
			return url;
		}

		public String username() {
			return username;
		}

		public String password() {
			return password;
		}

		public String driverClassName() {
			return driverClassName;
		}
	}

	/**
	 * Whether the given datasource URL belongs to a shared dev-service holder
	 * rather than a user-supplied one. The test database config delegates
	 * connection acquisition to the holder when this returns true, so the holder
	 * owns the single shared {@link DataSource} pool.
	 *
	 * @param url
	 *            the URL a {@code @Replaces} config would otherwise open directly
	 * @return true if the URL is the shared dev-services URL
	 */
	boolean owns(String url);

	/**
	 * Returns the single shared {@link DataSource} for a URL this holder owns.
	 * Called by the test database config so every universe in the JVM funnels
	 * through one Hikari pool. Must not be called for URLs {@link #owns} rejects.
	 */
	DataSource sharedDataSource(String url);
}
