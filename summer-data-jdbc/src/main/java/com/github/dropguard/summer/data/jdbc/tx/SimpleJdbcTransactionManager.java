mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.data.jdbc.tx;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.tx.SummerTransactionException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.tx.TransactionManager;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.tx.TransactionStatus;
mport com.github.dropguard.summer.core.Internal;
import java.sql.Connection;
mport com.github.dropguard.summer.core.Internal;
import java.sql.SQLException;
@Internal
mport com.github.dropguard.summer.core.Internal;
import javax.sql.DataSource;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Simple JDBC transaction manager that manages transactions using a DataSource.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is a framework infrastructure bean provided by {@link TxInfrastructureConfiguration}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
@ConditionalOnBean(DataSource.class)
mport com.github.dropguard.summer.core.Internal;
public class SimpleJdbcTransactionManager implements TransactionManager {
mport com.github.dropguard.summer.core.Internal;
    private final DataSource dataSource;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public SimpleJdbcTransactionManager(DataSource dataSource) {
mport com.github.dropguard.summer.core.Internal;
        this.dataSource = dataSource;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public TransactionStatus begin() {
mport com.github.dropguard.summer.core.Internal;
        Connection existing = ThreadLocalTransactionContext.getCurrentConnection();
mport com.github.dropguard.summer.core.Internal;
        if (existing != null) {
mport com.github.dropguard.summer.core.Internal;
            throw new SummerTransactionException(
mport com.github.dropguard.summer.core.Internal;
                    "Nested transactions are not supported. A transaction is already active for the"
mport com.github.dropguard.summer.core.Internal;
                            + " current thread.");
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Connection connection = null;
mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            connection = dataSource.getConnection();
mport com.github.dropguard.summer.core.Internal;
            connection.setAutoCommit(false);
mport com.github.dropguard.summer.core.Internal;
            return new ThreadLocalTransactionContext(connection, true);
mport com.github.dropguard.summer.core.Internal;
        } catch (SQLException e) {
mport com.github.dropguard.summer.core.Internal;
            if (connection != null) {
mport com.github.dropguard.summer.core.Internal;
                try {
mport com.github.dropguard.summer.core.Internal;
                    connection.close();
mport com.github.dropguard.summer.core.Internal;
                } catch (SQLException ignored) {
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            throw new SummerTransactionException("Failed to begin transaction", e);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void commit(TransactionStatus status) {
mport com.github.dropguard.summer.core.Internal;
        if (status instanceof ThreadLocalTransactionContext txContext) {
mport com.github.dropguard.summer.core.Internal;
            if (!txContext.isNewTransaction()) {
mport com.github.dropguard.summer.core.Internal;
                return;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            try {
mport com.github.dropguard.summer.core.Internal;
                Connection raw = txContext.getRawConnection();
mport com.github.dropguard.summer.core.Internal;
                if (!raw.getAutoCommit()) {
mport com.github.dropguard.summer.core.Internal;
                    raw.commit();
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            } catch (SQLException e) {
mport com.github.dropguard.summer.core.Internal;
                try {
mport com.github.dropguard.summer.core.Internal;
                    txContext.getRawConnection().rollback();
mport com.github.dropguard.summer.core.Internal;
                } catch (SQLException rollbackEx) {
mport com.github.dropguard.summer.core.Internal;
                    e.addSuppressed(rollbackEx);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                throw new SummerTransactionException("Failed to commit transaction", e);
mport com.github.dropguard.summer.core.Internal;
            } finally {
mport com.github.dropguard.summer.core.Internal;
                txContext.close();
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public void rollback(TransactionStatus status) {
mport com.github.dropguard.summer.core.Internal;
        if (status instanceof ThreadLocalTransactionContext txContext) {
mport com.github.dropguard.summer.core.Internal;
            if (!txContext.isNewTransaction()) {
mport com.github.dropguard.summer.core.Internal;
                txContext.setRollbackOnly();
mport com.github.dropguard.summer.core.Internal;
                return;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            try {
mport com.github.dropguard.summer.core.Internal;
                Connection raw = txContext.getRawConnection();
mport com.github.dropguard.summer.core.Internal;
                if (!raw.getAutoCommit()) {
mport com.github.dropguard.summer.core.Internal;
                    raw.rollback();
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            } catch (SQLException e) {
mport com.github.dropguard.summer.core.Internal;
                throw new SummerTransactionException("Failed to rollback transaction", e);
mport com.github.dropguard.summer.core.Internal;
            } finally {
mport com.github.dropguard.summer.core.Internal;
                txContext.close();
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
