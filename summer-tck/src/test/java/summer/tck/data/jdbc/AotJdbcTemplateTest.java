package summer.tck.data.jdbc;

import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;
import summer.data.jdbc.JdbcTemplate;

import javax.sql.DataSource;

/**
 * AOT engine JdbcTemplate TCK. Simulates the JdbcTemplate registration
 * produced by AOT-generated code: mappers registered directly on the
 * JdbcTemplate via {@code registerMapper}.
 */
public class AotJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected JdbcTemplate createJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jt = new JdbcTemplate(dataSource);
		jt.registerMapper(User.class, new User_RowMapper());
		return jt;
	}
}
