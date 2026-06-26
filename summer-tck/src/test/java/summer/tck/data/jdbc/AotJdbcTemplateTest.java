package summer.tck.data.jdbc;

import javax.sql.DataSource;
import summer.data.jdbc.JdbcTemplate;
import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;

/**
 * AOT engine JdbcTemplate TCK. Simulates the JdbcTemplate registration produced
 * by AOT-generated code: mappers registered directly on the JdbcTemplate via
 * {@code registerMapper}.
 */
public class AotJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected JdbcTemplate createJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jt = new JdbcTemplate(dataSource);
		jt.registerMapper(User.class, new User_RowMapper());
		return jt;
	}
}
