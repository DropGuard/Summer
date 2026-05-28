package summer.data.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import summer.core.exception.DataAccessException;

/**
 * Core JDBC operations class. Thread-safe and designed to be a singleton.
 * Integrates seamlessly with summer-tx's TransactionAwareDataSourceProxy.
 */
public class JdbcTemplate {

	private final DataSource dataSource;

	public JdbcTemplate(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public int update(String sql, Object... args) {
		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			setParameters(ps, args);
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new DataAccessException("Error executing update", e);
		}
	}

	public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			for (Object[] args : batchArgs) {
				setParameters(ps, args);
				ps.addBatch();
			}
			return ps.executeBatch();
		} catch (SQLException e) {
			throw new DataAccessException("Error executing batch update", e);
		}
	}

	public <T> List<T> queryForList(String sql, Class<T> rowType, Object... args) {
		RowMapper<T> mapper = RowMapperRegistry.getMapper(rowType);
		List<T> results = new ArrayList<>();
		try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
			setParameters(ps, args);
			try (ResultSet rs = ps.executeQuery()) {
				int rowNum = 0;
				while (rs.next()) {
					results.add(mapper.mapRow(rs, rowNum++));
				}
			}
			return results;
		} catch (SQLException e) {
			throw new DataAccessException("Error executing query", e);
		}
	}

	public <T> T queryForObject(String sql, Class<T> rowType, Object... args) {
		List<T> results = queryForList(sql, rowType, args);
		if (results.isEmpty()) {
			return null;
		}
		if (results.size() > 1) {
			throw new DataAccessException("Query returned more than one row");
		}
		return results.get(0);
	}

	private void setParameters(PreparedStatement ps, Object... args) throws SQLException {
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				ps.setObject(i + 1, args[i]);
			}
		}
	}
}
