package com.github.dropguard.summer.data.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface used by JdbcTemplate for mapping rows of a ResultSet on a per-row
 * basis. Implementations of this interface perform the actual work of mapping
 * each row to a result object, but don't need to worry about exception
 * handling.
 */
@FunctionalInterface
public interface RowMapper<T> {

	/**
	 * Map a single row of the ResultSet to a Java object.
	 *
	 * @param rs
	 *            the ResultSet to map (pre-initialized for the current row)
	 * @param rowNum
	 *            the number of the current row
	 * @return the result object for the current row
	 * @throws SQLException
	 *             if a SQLException is encountered getting column values
	 */
	T mapRow(ResultSet rs, int rowNum) throws SQLException;

}
