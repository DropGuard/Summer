mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc.tx;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.sql.*;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.Properties;
mport com.github.dropguard.summer.core.Internal;
@Internal
import java.util.concurrent.Executor;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Wraps a java.sql.Connection to suppress close(), commit(), and rollback() calls, leaving
mport com.github.dropguard.summer.core.Internal;
 * transaction management to the Summer @Transactional interceptor.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class TransactionAwareConnectionWrapper implements Connection {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Connection target;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public TransactionAwareConnectionWrapper(Connection target) {
mport com.github.dropguard.summer.core.Internal;
        this.target = target;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void close() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        // Suppressed: Handled by Summer Transaction Manager
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void commit() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        // Suppressed: Handled by Summer Transaction Manager
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void rollback() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        // Suppressed: Handled by Summer Transaction Manager
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void rollback(Savepoint savepoint) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        // Suppressed: Handled by Summer Transaction Manager
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ------------------------------------------------------------------------
mport com.github.dropguard.summer.core.Internal;
    // Delegated methods
mport com.github.dropguard.summer.core.Internal;
    // ------------------------------------------------------------------------
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Statement createStatement() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createStatement();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public PreparedStatement prepareStatement(String sql) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareStatement(sql);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public CallableStatement prepareCall(String sql) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareCall(sql);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String nativeSQL(String sql) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.nativeSQL(sql);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setAutoCommit(boolean autoCommit) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setAutoCommit(autoCommit);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean getAutoCommit() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getAutoCommit();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isClosed() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.isClosed();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public DatabaseMetaData getMetaData() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getMetaData();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setReadOnly(boolean readOnly) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setReadOnly(readOnly);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isReadOnly() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.isReadOnly();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setCatalog(String catalog) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setCatalog(catalog);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String getCatalog() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getCatalog();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setTransactionIsolation(int level) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setTransactionIsolation(level);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public int getTransactionIsolation() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getTransactionIsolation();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public SQLWarning getWarnings() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getWarnings();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void clearWarnings() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.clearWarnings();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Statement createStatement(int resultSetType, int resultSetConcurrency)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createStatement(resultSetType, resultSetConcurrency);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public PreparedStatement prepareStatement(
mport com.github.dropguard.summer.core.Internal;
            String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareStatement(sql, resultSetType, resultSetConcurrency);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareCall(sql, resultSetType, resultSetConcurrency);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Map<String, Class<?>> getTypeMap() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getTypeMap();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setTypeMap(map);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setHoldability(int holdability) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setHoldability(holdability);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public int getHoldability() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getHoldability();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Savepoint setSavepoint() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.setSavepoint();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Savepoint setSavepoint(String name) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.setSavepoint(name);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.releaseSavepoint(savepoint);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Statement createStatement(
mport com.github.dropguard.summer.core.Internal;
            int resultSetType, int resultSetConcurrency, int resultSetHoldability)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public PreparedStatement prepareStatement(
mport com.github.dropguard.summer.core.Internal;
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareStatement(
mport com.github.dropguard.summer.core.Internal;
                sql, resultSetType, resultSetConcurrency, resultSetHoldability);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public CallableStatement prepareCall(
mport com.github.dropguard.summer.core.Internal;
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareStatement(sql, autoGeneratedKeys);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareStatement(sql, columnIndexes);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public PreparedStatement prepareStatement(String sql, String[] columnNames)
mport com.github.dropguard.summer.core.Internal;
            throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.prepareStatement(sql, columnNames);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Clob createClob() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createClob();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Blob createBlob() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createBlob();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public NClob createNClob() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createNClob();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public SQLXML createSQLXML() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createSQLXML();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isValid(int timeout) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.isValid(timeout);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
mport com.github.dropguard.summer.core.Internal;
        target.setClientInfo(name, value);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
mport com.github.dropguard.summer.core.Internal;
        target.setClientInfo(properties);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String getClientInfo(String name) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getClientInfo(name);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Properties getClientInfo() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getClientInfo();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createArrayOf(typeName, elements);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.createStruct(typeName, attributes);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setSchema(String schema) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setSchema(schema);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public String getSchema() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getSchema();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void abort(Executor executor) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.abort(executor);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        target.setNetworkTimeout(executor, milliseconds);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public int getNetworkTimeout() throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.getNetworkTimeout();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public <T> T unwrap(Class<T> iface) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.unwrap(iface);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
mport com.github.dropguard.summer.core.Internal;
        return target.isWrapperFor(iface);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
