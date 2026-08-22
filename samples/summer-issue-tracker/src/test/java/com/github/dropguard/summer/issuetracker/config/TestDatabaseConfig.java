package com.github.dropguard.summer.issuetracker.config;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Test-only swap of the production {@link DatabaseConfig}. Reads the JDBC URL that {@link
 * AbstractIssueTrackerIT} publishes from the Testcontainers Postgres and opens its own pool. The
 * production config stays the single source of truth; the test environment only chooses which
 * database to point at.
 */
@Configuration
@Replaces(DatabaseConfig.class)
public class TestDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getProperty("com.github.dropguard.summer.test.datasource.url");
        if (url == null) {
            throw new IllegalStateException(
                    "Issue Tracker IT requires a real database. AbstractIssueTrackerIT sets"
                            + " com.github.dropguard.summer.test.datasource.url to a Testcontainers"
                            + " Postgres URL.");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(
                System.getProperty("com.github.dropguard.summer.test.datasource.username", "test"));
        config.setPassword(
                System.getProperty("com.github.dropguard.summer.test.datasource.password", "test"));
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
