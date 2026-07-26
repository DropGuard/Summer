package summer.issuetracker.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.data.jdbc.JdbcTemplate;

@Configuration
public class DatabaseConfig {

	@Bean
	public DataSource dataSource(DataSourceProperties props) {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(props.url());
		config.setUsername(props.username());
		config.setPassword(props.password());
		config.setDriverClassName(props.driverClassName());
		config.setMaximumPoolSize(10);
		return new HikariDataSource(config);
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}
}
