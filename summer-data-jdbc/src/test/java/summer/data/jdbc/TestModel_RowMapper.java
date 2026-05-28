package summer.data.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TestModel_RowMapper implements RowMapper<TestModel> {
	@Override
	public TestModel mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new TestModel(rs.getInt("id"), rs.getString("name"));
	}
}
