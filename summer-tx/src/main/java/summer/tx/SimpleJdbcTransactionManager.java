package summer.tx;

import summer.core.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Simple JDBC transaction manager that manages transactions using JDBC connections.
 */
@Component
public class SimpleJdbcTransactionManager implements TransactionManager {
    private final String url;
    private final String username;
    private final String password;

    public SimpleJdbcTransactionManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public TransactionStatus begin() {
        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            connection.setAutoCommit(false);
            return new ThreadLocalTransactionContext(connection);
        } catch (SQLException e) {
            throw new SummerTransactionException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commit(TransactionStatus status) {
        if (status instanceof ThreadLocalTransactionContext txContext) {
            try {
                Connection connection = txContext.getConnection();
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
            } catch (SQLException e) {
                try {
                    txContext.getConnection().rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
                throw new SummerTransactionException("Failed to commit transaction", e);
            } finally {
                txContext.close();
            }
        }
    }

    @Override
    public void rollback(TransactionStatus status) {
        if (status instanceof ThreadLocalTransactionContext txContext) {
            try {
                Connection connection = txContext.getConnection();
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (SQLException e) {
                throw new SummerTransactionException("Failed to rollback transaction", e);
            } finally {
                txContext.close();
            }
        }
    }
}