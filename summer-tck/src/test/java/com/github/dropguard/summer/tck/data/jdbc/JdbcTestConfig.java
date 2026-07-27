package com.github.dropguard.summer.tck.data.jdbc;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Test configuration that registers a {@link DataSource} and {@link JdbcTemplate} as real DI beans
 * — so both engines (Runtime + AOT) wire them through the container rather than via {@code new
 * JdbcTemplate(...)}.
 *
 * <p>With {@link JdbcTemplate} present as a bean, {@code ReflectiveRowMapperRegistrar} (Runtime
 * engine) auto-registers every {@code @RowModel} it discovers through the unified test universe
 * (the deployment's discovery index combines {@code jandex.idx} with the running test class's
 * {@code test-classes} directory, indexed on demand). The test-tree {@code @RowModel} {@code User}
 * is therefore discovered and mapped on BOTH engines — the real dual-engine path this TCK must
 * verify. No manual {@code registerMapper} here: doing so would bypass {@code
 * ReflectiveRowMapperRegistrar} and hide exactly the gap this TCK exists to catch.
 *
 * <p>The previous TCK bypassed the container with a manually-constructed {@code JdbcTemplate} whose
 * Runtime/AOT bodies were byte-identical, so it was a decoy with no real engine differentiation.
 */
@Configuration
public class JdbcTestConfig {

    @Bean
    public DataSource jdbcTestDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:tck_jdbc;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        // Bare H2 on purpose: users never wrap their DataSource by hand. Connection
        // sharing between @Transactional and JdbcTemplate is handled inside
        // JdbcTemplate itself (it reuses the active transaction's connection), so
        // @Transactional rolls back as expected with zero config. This TCK verifies
        // exactly that — no manual proxy anywhere.
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTestJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
