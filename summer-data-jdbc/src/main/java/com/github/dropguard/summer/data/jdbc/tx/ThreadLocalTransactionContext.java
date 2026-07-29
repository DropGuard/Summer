mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc.tx;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.tx.SummerTransactionException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.tx.TransactionStatus;
@Internal
mport com.github.dropguard.summer.core.Internal;
import java.sql.Connection;
mport com.github.dropguard.summer.core.Internal;
import java.sql.SQLException;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/** ThreadLocal based transaction context that manages connections per thread. */
mport com.github.dropguard.summer.core.Internal;
public class ThreadLocalTransactionContext implements TransactionStatus {
mport com.github.dropguard.summer.core.Internal;
    private static final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();
mport com.github.dropguard.summer.core.Internal;
    private boolean rollbackOnly = false;
mport com.github.dropguard.summer.core.Internal;
    private boolean active = true;
mport com.github.dropguard.summer.core.Internal;
    private final boolean isNewTransaction;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Connection rawConnection;
mport com.github.dropguard.summer.core.Internal;
    private final Connection wrappedConnection;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public ThreadLocalTransactionContext(Connection connection, boolean isNewTransaction) {
mport com.github.dropguard.summer.core.Internal;
        this.rawConnection = connection;
mport com.github.dropguard.summer.core.Internal;
        this.wrappedConnection = new TransactionAwareConnectionWrapper(connection);
mport com.github.dropguard.summer.core.Internal;
        this.isNewTransaction = isNewTransaction;
mport com.github.dropguard.summer.core.Internal;
        if (isNewTransaction) {
mport com.github.dropguard.summer.core.Internal;
            connectionThreadLocal.set(wrappedConnection);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public static Connection getCurrentConnection() {
mport com.github.dropguard.summer.core.Internal;
        return connectionThreadLocal.get();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public static void clearCurrentConnection() {
mport com.github.dropguard.summer.core.Internal;
        connectionThreadLocal.remove();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isActive() {
mport com.github.dropguard.summer.core.Internal;
        return active;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isNewTransaction() {
mport com.github.dropguard.summer.core.Internal;
        return isNewTransaction;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean isRollbackOnly() {
mport com.github.dropguard.summer.core.Internal;
        return rollbackOnly;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void setRollbackOnly() {
mport com.github.dropguard.summer.core.Internal;
        this.rollbackOnly = true;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void flush() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public Connection getRawConnection() {
mport com.github.dropguard.summer.core.Internal;
        return rawConnection;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public void close() {
mport com.github.dropguard.summer.core.Internal;
        active = false;
mport com.github.dropguard.summer.core.Internal;
        if (isNewTransaction) {
mport com.github.dropguard.summer.core.Internal;
            clearCurrentConnection();
mport com.github.dropguard.summer.core.Internal;
            try {
mport com.github.dropguard.summer.core.Internal;
                if (rawConnection != null && !rawConnection.isClosed()) {
mport com.github.dropguard.summer.core.Internal;
                    rawConnection.close();
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            } catch (SQLException e) {
mport com.github.dropguard.summer.core.Internal;
                throw new SummerTransactionException("Failed to close connection", e);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
