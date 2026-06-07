package summer.tx;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import summer.core.ErrorCode;
import summer.core.annotation.ConditionalOnBean;

/**
 * Simple JDBC transaction manager that manages transactions using a DataSource.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link TxInfrastructureConfiguration}.
 * </p>
 */
@ConditionalOnBean(DataSource.class)
public class SimpleJdbcTransactionManager implements TransactionManager {
	private final DataSource dataSource;

	public SimpleJdbcTransactionManager(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public TransactionStatus begin() {
		Connection existing = ThreadLocalTransactionContext.getCurrentConnection();
		if (existing != null) {
			throw new SummerTransactionException(ErrorCode.TRANSACTION_ERROR,
					"Nested transactions are not supported. A transaction is already active for the current thread.");
		}

		Connection connection = null;
		try {
			connection = dataSource.getConnection();
			connection.setAutoCommit(false);
			return new ThreadLocalTransactionContext(connection, true);
		} catch (SQLException e) {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException ignored) {
					// Already in error path; closing failure is secondary
				}
			}
			throw new SummerTransactionException(ErrorCode.TRANSACTION_ERROR, "Failed to begin transaction", e);
		}
	}

	@Override
	public void commit(TransactionStatus status) {
		if (status instanceof ThreadLocalTransactionContext txContext) {
			if (!txContext.isNewTransaction()) {
				return;
			}
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
				throw new SummerTransactionException(ErrorCode.TRANSACTION_ERROR, "Failed to commit transaction", e);
			} finally {
				txContext.close();
			}
		}
	}

	@Override
	public void rollback(TransactionStatus status) {
		if (status instanceof ThreadLocalTransactionContext txContext) {
			if (!txContext.isNewTransaction()) {
				txContext.setRollbackOnly();
				return;
			}
			try {
				Connection connection = txContext.getConnection();
				if (!connection.getAutoCommit()) {
					connection.rollback();
				}
			} catch (SQLException e) {
				throw new SummerTransactionException(ErrorCode.TRANSACTION_ERROR, "Failed to rollback transaction", e);
			} finally {
				txContext.close();
			}
		}
	}
}
