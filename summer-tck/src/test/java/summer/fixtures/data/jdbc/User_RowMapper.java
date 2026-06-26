package summer.fixtures.data.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import summer.data.jdbc.RowMapper;

/**
 * Manually created RowMapper for TCK tests (equivalent to AOT-generated
 * mapper).
 */
public class User_RowMapper implements RowMapper<User> {

	@Override
	public User mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new User(rs.getInt("id"), rs.getString("name"));
	}
}
