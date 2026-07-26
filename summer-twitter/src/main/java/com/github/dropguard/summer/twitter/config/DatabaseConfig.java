package com.github.dropguard.summer.twitter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.data.jdbc.JdbcTemplate;

@ConfigurationProperties(prefix = "com.github.dropguard.summer.datasource")
record DataSourceProperties(String url, String username, String password, String driverClassName) {
}

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties props) {
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
