package summer.it;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.data.jdbc.JdbcTemplate;
import summer.test.internal.DevServicesHolder;
import summer.test.internal.SummerTestLifecycle;

/**
 * Framework-IT database config: replaces production {@code DatabaseConfig} and
 * routes {@code summer:devservices:postgres} to the shared dev-services pool.
 *
 * <p>
 * This is an <b>internal</b> test fixture: it uses {@code summer.test.internal}
 * directly because it is part of the framework's own integration test module.
 * Demos must NOT copy this — they use the public path (set
 * {@code summer.test.datasource.url} to a real JDBC URL themselves).
 * </p>
 */
@Configuration
public class ItDatabaseConfig {

	@Bean
	public DataSource dataSource() {
		String url = System.getProperty("summer.test.datasource.url");
		if (url == null) {
			throw new IllegalStateException(
					"Framework IT requires a real database. Call SummerTestLifecycle.ensureDevServices(...) "
							+ "first (it sets summer.test.datasource.url), or set it explicitly.");
		}
		DevServicesHolder devServices = SummerTestLifecycle.instance().devServices();
		if (devServices.owns(url)) {
			return devServices.sharedDataSource(url);
		}
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url);
		config.setUsername(System.getProperty("summer.test.datasource.username", "test"));
		config.setPassword(System.getProperty("summer.test.datasource.password", "test"));
		config.setDriverClassName("org.postgresql.Driver");
		config.setMaximumPoolSize(2);
		return new HikariDataSource(config);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
