package summer.data.jdbc.tx;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import summer.core.annotation.ConditionalOnBean;
import summer.tx.SummerTransactionException;
import summer.tx.TransactionManager;
import summer.tx.TransactionStatus;

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
			throw new SummerTransactionException(
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
				}
			}
			throw new SummerTransactionException("Failed to begin transaction", e);
		}
	}

	@Override
	public void commit(TransactionStatus status) {
		if (status instanceof ThreadLocalTransactionContext txContext) {
			if (!txContext.isNewTransaction()) {
				return;
			}
			try {
				Connection raw = txContext.getRawConnection();
				if (!raw.getAutoCommit()) {
					raw.commit();
				}
			} catch (SQLException e) {
				try {
					txContext.getRawConnection().rollback();
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
			if (!txContext.isNewTransaction()) {
				txContext.setRollbackOnly();
				return;
			}
			try {
				Connection raw = txContext.getRawConnection();
				if (!raw.getAutoCommit()) {
					raw.rollback();
				}
			} catch (SQLException e) {
				throw new SummerTransactionException("Failed to rollback transaction", e);
			} finally {
				txContext.close();
			}
		}
	}
}
