package summer.tx;

import java.sql.Connection;
import java.sql.SQLException;
import summer.core.ErrorCode;

/**
 * ThreadLocal based transaction context that manages connections per thread.
 */
public class ThreadLocalTransactionContext implements TransactionStatus {
	private static final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();
	private boolean rollbackOnly = false;
	private boolean active = true;
	private final boolean isNewTransaction;

	private final Connection connection;

	public ThreadLocalTransactionContext(Connection connection, boolean isNewTransaction) {
		this.connection = connection;
		this.isNewTransaction = isNewTransaction;
		if (isNewTransaction) {
			connectionThreadLocal.set(connection);
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

	@Override
	public void flush() {
		// JDBC Connection doesn't have flush method, so this is a no-op
	}

	public Connection getConnection() {
		return connection;
	}

	public void close() {
		active = false;
		if (isNewTransaction) {
			clearCurrentConnection();
			try {
				if (connection != null && !connection.isClosed()) {
					connection.close();
				}
			} catch (SQLException e) {
				throw new SummerTransactionException(ErrorCode.TRANSACTION_ERROR, "Failed to close connection", e);
			}
		}
	}
}