package summer.twitter.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.config.ConfigurationProperties;
import summer.data.jdbc.JdbcTemplate;
import summer.data.jdbc.tx.TransactionAwareDataSourceProxy;

@ConfigurationProperties(prefix = "summer.datasource")
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
        
        HikariDataSource hikariDataSource = new HikariDataSource(config);
        return new TransactionAwareDataSourceProxy(hikariDataSource);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
