package com.github.dropguard.summer.twitter.config;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Test-only database configuration that replaces production {@code DatabaseConfig}.
 *
 * <p>Twitter validates {@code summer-data-jdbc} against a <b>real</b> database — it never uses an
 * in-memory stand-in. This demo uses ONLY the public test API: {@code AbstractTwitterIT} starts
 * Testcontainers directly and sets the {@code com.github.dropguard.summer.test.datasource.url}
 * system property; this config simply reads that property and opens its own small pool. No {@code
 * com.github.dropguard.summer.test.internal} type is used — that is reserved for framework-owned
 * integration tests.
 *
 * <p>This is a class-level {@code @Replaces} swap, not a mock of {@code JdbcTemplate}/{@code
 * DataSource}: production {@code DatabaseConfig} stays the single source of truth and is untouched.
 * The only thing the test environment owns is <em>which</em> database to connect to.
 */
@Configuration
@Replaces(DatabaseConfig.class)
public class TestDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getProperty("com.github.dropguard.summer.test.datasource.url");
        if (url == null) {
            throw new IllegalStateException(
                    "Twitter tests require a real database. AbstractTwitterIT sets"
                        + " com.github.dropguard.summer.test.datasource.url to a real JDBC URL —"
                        + " start the test via the @Container-managed Postgres. In-memory stand-ins"
                        + " are intentionally not supported so summer-data-jdbc is always exercised"
                        + " against a real engine.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(
                System.getProperty("com.github.dropguard.summer.test.datasource.username", "test"));
        config.setPassword(
                System.getProperty("com.github.dropguard.summer.test.datasource.password", "test"));
        config.setDriverClassName("org.postgresql.Driver");
        // Small pool: one per @SummerTest universe. Twitter's IT classes are few, so the
        // total connection count stays well under Postgres' max_connections.
        config.setMaximumPoolSize(2);

        HikariDataSource hikariDataSource = new HikariDataSource(config);
        return hikariDataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
