package summer.twitter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.tx.TransactionAwareDataSourceProxy;
import summer.test.DevServicesHolder;
import summer.test.TestRunContext;

/**
 * Test-only database configuration that replaces production {@code DatabaseConfig}.
 *
 * <p>
 * Twitter validates {@code summer-data-jdbc} against a <b>real</b> database — it
 * never uses an in-memory stand-in. Two real-DB modes are supported, both driven
 * by the {@code summer.test.datasource.url} system property (no annotation
 * switch needed):
 * <ul>
 * <li><b>Shared dev-services</b> — the IT starts a single Postgres via
 * {@link TestRunContext} and points this config at the shared URL scheme. The
 * config then reuses the holder's single Hikari pool, so every universe in the
 * JVM funnels through one pool and Postgres' {@code max_connections} is never
 * exhausted.</li>
 * <li><b>Explicit URL</b> — a URL set directly (e.g. by an external harness)
 * opens its own small pool.</li>
 * </ul>
 * </p>
 *
 * <p>
 * This is a class-level {@code @Replaces} swap, not a mock of
 * {@code JdbcTemplate}/{@code DataSource}: production {@code DatabaseConfig}
 * stays the single source of truth and is untouched. The only thing the test
 * environment owns is <em>which</em> database to connect to.
 * </p>
 */
@Configuration
@Replaces(DatabaseConfig.class)
public class TestDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getProperty("summer.test.datasource.url");
        if (url == null) {
            throw new IllegalStateException(
                    "Twitter tests require a real database. Start the shared dev-services "
                            + "(TestRunContext.ensureDevServices) which sets summer.test.datasource.url, "
                            + "or set it explicitly. In-memory stand-ins are intentionally not supported "
                            + "here so summer-data-jdbc is always exercised against a real engine.");
        }

        // Shared dev-services mode: reuse the holder's one pool for the whole JVM.
        DevServicesHolder devServices = TestRunContext.instance().devServices();
        if (devServices.owns(url)) {
            return new TransactionAwareDataSourceProxy(devServices.sharedDataSource(url));
        }

        String username = System.getProperty("summer.test.datasource.username", "summer");
        String password = System.getProperty("summer.test.datasource.password", "summer");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        // Small pool: used only when an explicit (non-shared) URL is supplied.
        config.setMaximumPoolSize(2);

        HikariDataSource hikariDataSource = new HikariDataSource(config);
        return new TransactionAwareDataSourceProxy(hikariDataSource);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
