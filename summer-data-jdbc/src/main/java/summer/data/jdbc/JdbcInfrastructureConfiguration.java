package summer.data.jdbc;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Configuration for JDBC infrastructure beans.
 *
 * <p>
 * Provides {@link RowMapperRegistry} which manages pre-compiled RowMapper
 * instances for database row mapping.
 * </p>
 */
@Configuration
public class JdbcInfrastructureConfiguration {

	@Bean
	public RowMapperRegistry rowMapperRegistry() {
		return new RowMapperRegistry();
	}
}
