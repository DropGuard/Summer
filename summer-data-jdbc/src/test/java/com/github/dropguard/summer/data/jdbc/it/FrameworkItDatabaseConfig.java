package com.github.dropguard.summer.data.jdbc.it;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Test DataSource provider for the data-jdbc module's real-Postgres integration tests.
 *
 * <p>Reads the JDBC coordinates from system properties set by the test's {@code @BeforeAll}
 * bootstrap — the same public convention that demos like {@code AbstractTwitterIT} use. No {@code
 * summer.test.internal} dependency.
 */
@Configuration
public class FrameworkItDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        String url = System.getProperty("com.github.dropguard.summer.test.datasource.url");
        if (url == null) {
            throw new IllegalStateException(
                    "Integration test requires a database. Set"
                            + " com.github.dropguard.summer.test.datasource.url before building"
                            + " the container.");
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
