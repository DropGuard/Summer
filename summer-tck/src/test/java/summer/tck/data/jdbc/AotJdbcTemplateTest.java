package summer.tck.data.jdbc;

import summer.data.jdbc.RowMapperRegistry;
import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;

/**
 * AOT JdbcTemplate TCK test. Simulates the RowMapperRegistry produced by
 * AOT-generated {@code RowMapperConfiguration}: mappers registered at
 * construction time, no reflection fallback.
 */
public class AotJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected RowMapperRegistry createRegistry() {
		// Mirrors what AOT-generated RowMapperConfiguration.rowMapperRegistry()
		// produces
		RowMapperRegistry registry = new RowMapperRegistry();
		registry.register(User.class, new User_RowMapper());
		return registry;
	}
}
