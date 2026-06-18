package summer.tck.data.jdbc;

import javax.sql.DataSource;
import summer.data.jdbc.JdbcTemplate;
import summer.fixtures.data.jdbc.User;
import summer.fixtures.data.jdbc.User_RowMapper;
/**
 * Runtime engine JdbcTemplate TCK. Simulates JdbcTemplate configuration
 * produced by Runtime-discovered {@code RowMapperConfiguration}:
 * same generated mappers, registered via {@code registerMapper}.
 */
public class RuntimeJdbcTemplateTest extends AbstractJdbcTemplateTCK {

	@Override
	protected JdbcTemplate createJdbcTemplate(DataSource dataSource) {
		JdbcTemplate jt = new JdbcTemplate(dataSource);
		jt.registerMapper(User.class, new User_RowMapper());
		return jt;
	}
}
