package summer.issuetracker.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;
import summer.data.jdbc.JdbcTemplate;

/**
 * Test-only swap of the production {@link DatabaseConfig}. Reads the JDBC URL
 * that {@link AbstractIssueTrackerIT} publishes from the Testcontainers Postgres
 * and opens its own pool. The production config stays the single source of
 * truth; the test environment only chooses which database to point at.
 */
@Configuration
@Replaces(DatabaseConfig.class)
public class TestDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getProperty("summer.test.datasource.url");
        if (url == null) {
            throw new IllegalStateException(
                    "Issue Tracker IT requires a real database. AbstractIssueTrackerIT sets "
                            + "summer.test.datasource.url to a Testcontainers Postgres URL.");
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
