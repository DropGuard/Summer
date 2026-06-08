package summer.tck.data.jdbc;

import summer.data.jdbc.RowMapperRegistry;
import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;

/**
 * Runtime engine JdbcTemplate TCK. Simulates the RowMapperRegistry produced by
 * Runtime-discovered {@code RowMapperConfiguration}: same generated mappers,
 * instantiated via reflection.
 */
public class RuntimeJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected RowMapperRegistry createRegistry() {
		RowMapperRegistry registry = new RowMapperRegistry();
		registry.register(User.class, new User_RowMapper());
		return registry;
	}
}
