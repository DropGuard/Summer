package com.github.dropguard.summer.data.jdbc.tx;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.tx.SummerTransactionException;
import com.github.dropguard.summer.tx.TransactionStatus;
import java.sql.Connection;
import java.sql.SQLException;

/** ThreadLocal based transaction context that manages connections per thread. */
@Internal
public class ThreadLocalTransactionContext implements TransactionStatus {
    private static final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();
    private boolean rollbackOnly = false;
    private boolean active = true;
    private final boolean isNewTransaction;

    private final Connection rawConnection;
    private final Connection wrappedConnection;

    public ThreadLocalTransactionContext(Connection connection, boolean isNewTransaction) {
        this.rawConnection = connection;
        this.wrappedConnection = new TransactionAwareConnectionWrapper(connection);
        this.isNewTransaction = isNewTransaction;
        if (isNewTransaction) {
            connectionThreadLocal.set(wrappedConnection);
        }
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
    public boolean isNewTransaction() {
        return isNewTransaction;
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    @Override
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    public Connection getRawConnection() {
        return rawConnection;
    }

    public void close() {
        active = false;
        if (isNewTransaction) {
            clearCurrentConnection();
            try {
                if (rawConnection != null && !rawConnection.isClosed()) {
                    rawConnection.close();
                }
            } catch (SQLException e) {
                throw new SummerTransactionException("Failed to close connection", e);
            }
        }
    }
}
