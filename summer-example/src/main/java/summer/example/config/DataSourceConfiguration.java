package summer.example.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.tx.TransactionAwareDataSourceProxy;

@Configuration
public class DataSourceConfiguration {

	@Bean
	public DataSource dataSource(DatabaseProperties props) {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(props.getUrl());
		config.setUsername(props.getUsername());
		config.setPassword("");
		HikariDataSource hikari = new HikariDataSource(config);
		return new TransactionAwareDataSourceProxy(hikari);
	}

	@Bean
	public summer.data.jdbc.JdbcTemplate jdbcTemplate(DataSource dataSource, summer.data.jdbc.RowMapperRegistry rowMapperRegistry) {
		return new summer.data.jdbc.JdbcTemplate(dataSource, rowMapperRegistry);
	}
}
