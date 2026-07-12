package summer.twitter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.tx.TransactionAwareDataSourceProxy;

/**
 * Test-only database configuration that replaces {@link DatabaseConfig}.
 * Reads dynamic PostgreSQL connection details from system properties
 * (set by the test harness from a Testcontainers container).
 *
 * <p>This avoids modifying {@code ConfigBinder} and keeps the production
 * configuration source of truth unchanged.</p>
 */
@Configuration
@Replaces(DatabaseConfig.class)
public class TestDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getProperty("summer.test.datasource.url",
                "jdbc:postgresql://localhost:5432/summer");
        String username = System.getProperty("summer.test.datasource.username", "summer");
        String password = System.getProperty("summer.test.datasource.password", "summer");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);

        HikariDataSource hikariDataSource = new HikariDataSource(config);
        return new TransactionAwareDataSourceProxy(hikariDataSource);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
