package com.github.dropguard.summer.aot.db;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Real-Postgres datasource config for {@link RealPostgresAotIntegrationIT}, bound from the
 * resource's overrides.
 */
@Configuration
public class AotRealPostgresConfig {

    @ConfigMapping(prefix = "datasource")
    public interface DataSourceProps {
        String url();

        String username();

        String password();

        String driverClassName();
    }

    @Bean
    public DataSource dataSource(DataSourceProps props) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.url());
        config.setUsername(props.username());
        config.setPassword(props.password());
        config.setDriverClassName(props.driverClassName());
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
