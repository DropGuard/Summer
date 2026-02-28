package summer.tx;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * ThreadLocal based transaction context that manages connections per thread.
 */
public class ThreadLocalTransactionContext implements TransactionStatus {
    private static final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();
    private boolean rollbackOnly = false;
    private boolean active = true;

    private final Connection connection;

    public ThreadLocalTransactionContext(Connection connection) {
        this.connection = connection;
        connectionThreadLocal.set(connection);
    }

    public static Connection getCurrentConnection() {
        return connectionThreadLocal.get();
    }

    public static void clearCurrentConnection() {
        connectionThreadLocal.remove();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    @Override
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    @Override
    public void flush() {
        // JDBC Connection doesn't have flush method, so this is a no-op
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        active = false;
        clearCurrentConnection();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new SummerTransactionException("Failed to close connection", e);
        }
    }
}