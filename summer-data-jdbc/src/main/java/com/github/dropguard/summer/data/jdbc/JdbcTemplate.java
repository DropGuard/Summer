package com.github.dropguard.summer.data.jdbc;

import com.github.dropguard.summer.core.FrozenState;
import com.github.dropguard.summer.core.Sealable;
import com.github.dropguard.summer.core.exception.DataAccessException;
import com.github.dropguard.summer.data.jdbc.tx.ScopedValueTransactionContext;
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
 * Core JDBC operations class. Singleton, and read-only after container assembly — the
 * write-once-then-read boundary is enforced, not just documented.
 *
 * <p>Thread-safety model: the {@code mappers} registry is written only during container assembly,
 * then read concurrently by queries (safe publication via {@link #state}). The boundary is the same
 * on both engines: assembly-time writers fill the mappers — the runtime engine's {@code
 * ReflectiveRowMapperRegistrar} bean, the AOT engine's generated {@code registerMapper} statements
 * — and the container's seal phase then calls {@link #seal()} (this bean implements {@link
 * Sealable}) once the whole graph is up. {@link #registerMapper} throws after that.
 *
 * <p>Connection acquisition is transaction-aware: when a {@code @Transactional} boundary is active
 * on the current thread, {@link #getConnection()} reuses the transaction's connection (so writes
 * inside the boundary commit/rollback together). Outside a transaction it falls back to the
 * underlying {@link DataSource}. Users never wrap their {@code DataSource} by hand — the framework
 * handles connection sharing here.
 */
public class JdbcTemplate implements Sealable {

    private final DataSource dataSource;
    private final Map<Class<?>, RowMapper<?>> mappers;
    private final FrozenState state = new FrozenState();

    /**
     * Mutable-construction path: assembly-time writers fill the mappers via {@link
     * #registerMapper}, and the container's seal phase freezes the state at the end of assembly.
     */
    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
        this.mappers = new HashMap<>();
    }

    /**
     * Resolves a connection, preferring the active transaction's connection when one exists on the
     * current thread. Exposed for callers that need raw JDBC access consistent with
     * {@code @Transactional} semantics.
     */
    public Connection getConnection() throws SQLException {
        Connection txConnection = ScopedValueTransactionContext.getCurrentConnection();
        if (txConnection != null) {
            return txConnection;
        }
        return dataSource.getConnection();
    }

    /**
     * Registers a {@code RowMapper} for the given row type. Assembly-time API — {@code @Internal}:
     * part of the framework's engine contract, not the public API. Filled by the runtime engine's
     * {@code ReflectiveRowMapperRegistrar} and the AOT engine's generated statements; throws once
     * the template is sealed (the container seal phase, post-assembly on both engines).
     */
    @com.github.dropguard.summer.core.Internal
    public void registerMapper(Class<?> rowType, RowMapper<?> mapper) {
        state.ensureMutable("registerMapper");
        mappers.put(rowType, mapper);
    }

    /**
     * Seals this template: further {@link #registerMapper} calls throw. Called by the container's
     * seal phase at the end of assembly (both engines); a caller may also seal earlier. Idempotent.
     */
    @Override
    public void seal() {
        state.freeze();
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
        return queryForList(sql, resolveMapper(rowType), args);
    }

    /**
     * Queries rows with an explicit {@link RowMapper}, for custom mappings that do not go through
     * the registered {@code @RowModel} mapper (joins, DTO projections, ad-hoc transforms).
     *
     * @param sql the SQL query
     * @param mapper the row mapper to apply
     * @param args bind parameters
     * @return mapped rows
     */
    public <T> List<T> queryForList(String sql, RowMapper<T> mapper, Object... args) {
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

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> resolveMapper(Class<T> rowType) {
        RowMapper<T> mapper = (RowMapper<T>) mappers.get(rowType);
        if (mapper != null) {
            return mapper;
        }
        return switch (rowType.getName()) {
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

    public <T> T queryForObject(String sql, Class<T> rowType, Object... args) {
        List<T> results = queryForList(sql, rowType, args);
        return singleResult(results);
    }

    /**
     * Queries a single row with an explicit {@link RowMapper} (custom mappings, DTO projections).
     *
     * @param sql the SQL query
     * @param mapper the row mapper to apply
     * @param args bind parameters
     * @return the mapped row, or {@code null} when no row matched
     * @throws DataAccessException when more than one row matched
     */
    public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... args) {
        List<T> results = queryForList(sql, mapper, args);
        return singleResult(results);
    }

    private <T> T singleResult(List<T> results) {
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
