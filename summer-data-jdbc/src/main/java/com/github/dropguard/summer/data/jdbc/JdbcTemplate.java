package com.github.dropguard.summer.data.jdbc;

import com.github.dropguard.summer.core.exception.DataAccessException;
import com.github.dropguard.summer.data.jdbc.tx.ThreadLocalTransactionContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Core JDBC operations class. Thread-safe and designed to be a singleton.
 *
 * <p>Connection acquisition is transaction-aware: when a {@code @Transactional} boundary is active
 * on the current thread, {@link #getConnection()} reuses the transaction's connection (so writes
 * inside the boundary commit/rollback together). Outside a transaction it falls back to the
 * underlying {@link DataSource}. Users never wrap their {@code DataSource} by hand — the framework
 * handles connection sharing here.
 */
public class JdbcTemplate {

    private final DataSource dataSource;
    private final Map<Class<?>, RowMapper<?>> mappers = new HashMap<>();

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Resolves a connection, preferring the active transaction's connection when one exists on the
     * current thread. Exposed for callers that need raw JDBC access consistent with
     * {@code @Transactional} semantics.
     */
    public Connection getConnection() throws SQLException {
        Connection txConnection = ThreadLocalTransactionContext.getCurrentConnection();
        if (txConnection != null) {
            return txConnection;
        }
        return dataSource.getConnection();
    }

    /**
     * Registers a {@code RowMapper} for the given row type. Called by the DI engine at startup; not
     * part of the public API.
     */
    public void registerMapper(Class<?> rowType, RowMapper<?> mapper) {
        mappers.put(rowType, mapper);
    }

    public int update(String sql, Object... args) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            setParameters(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Error executing update", e);
        }
    }

    public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] args : batchArgs) {
                setParameters(ps, args);
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("Error executing batch update", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> queryForList(String sql, Class<T> rowType, Object... args) {
        RowMapper<T> mapper = (RowMapper<T>) mappers.get(rowType);
        if (mapper == null) {
            mapper =
                    switch (rowType.getName()) {
                        case "java.lang.Long" ->
                                (rs, rowNum) -> {
                                    long val = rs.getLong(1);
                                    return rs.wasNull() ? null : (T) Long.valueOf(val);
                                };
                        case "java.lang.Integer" ->
                                (rs, rowNum) -> {
                                    int val = rs.getInt(1);
                                    return rs.wasNull() ? null : (T) Integer.valueOf(val);
                                };
                        case "java.lang.String" -> (rs, rowNum) -> (T) rs.getString(1);
                        case "java.lang.Boolean" ->
                                (rs, rowNum) -> {
                                    boolean val = rs.getBoolean(1);
                                    return rs.wasNull() ? null : (T) Boolean.valueOf(val);
                                };
                        default ->
                                throw new DataAccessException(
                                        "No RowMapper registered for "
                                                + rowType.getName()
                                                + ". Ensure the class is annotated with @RowModel"
                                                + " and summer-maven-plugin is configured.");
                    };
        }
        List<T> results = new ArrayList<>();
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
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
