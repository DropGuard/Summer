package summer.tck.data.jdbc;

import summer.data.jdbc.RowMapperRegistry;
import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;

/**
 * AOT engine JdbcTemplate TCK. Simulates the RowMapperRegistry produced by
 * AOT-generated {@code RowMapperConfiguration}: mappers registered via
 * compile-time generated code.
 */
public class AotJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected RowMapperRegistry createRegistry() {
		// Mirrors what AOT-generated RowMapperConfiguration.rowMapperRegistry()
		// produces
		RowMapperRegistry registry = new RowMapperRegistry();
		registry.put(User.class, new User_RowMapper());
		return registry;
	}
}
