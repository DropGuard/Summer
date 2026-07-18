package summer.data.jdbc;

import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;

/**
 * Wires {@link RowMapperRegistrar} into the container.
 *
 * <p>
 * RowMapper registration is a data-module concern owned by this module; the DI
 * engines no longer reach across module boundaries to wire mappers. The
 * registrar depends on {@link JdbcTemplate}, which is an application-provided
 * bean (not a framework bean), so this configuration is conditional on its
 * presence — when no {@code JdbcTemplate} is registered (no JDBC usage), no
 * registrar and no mappers are created, matching the previous runtime behaviour
 * of silently skipping registration.
 * </p>
 */
@Configuration
@ConditionalOnBean(JdbcTemplate.class)
public class RowMapperConfiguration {

	@Bean
	public RowMapperRegistrar rowMapperRegistrar(JdbcTemplate jdbcTemplate) {
		return new RowMapperRegistrar(jdbcTemplate);
	}
}
