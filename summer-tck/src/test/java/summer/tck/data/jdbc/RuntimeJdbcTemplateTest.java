package summer.tck.data.jdbc;

import summer.data.jdbc.RowMapperRegistry;
import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;

/**
 * Runtime (reflection-based) JdbcTemplate TCK test.
 */
public class RuntimeJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected RowMapperRegistry createRegistry() {
		RowMapperRegistry registry = new RowMapperRegistry();
		registry.register(User.class, new User_RowMapper());
		return registry;
	}
}
