package summer.tx;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * DataSource proxy that returns the transactional connection when a transaction
 * is active, or delegates to the target DataSource otherwise.
 *
 * <p>
 * When a transaction is active, the returned connection is wrapped to suppress
 * {@code close()}, {@code commit()}, and {@code rollback()} calls — these are
 * managed by the {@link TransactionInterceptor}.
 * </p>
 */
public class TransactionAwareDataSourceProxy implements DataSource {

	private final DataSource targetDataSource;

	public TransactionAwareDataSourceProxy(DataSource targetDataSource) {
		this.targetDataSource = targetDataSource;
	}

	@Override
	public Connection getConnection() throws SQLException {
		Connection txConnection = ThreadLocalTransactionContext.getCurrentConnection();
		if (txConnection != null) {
			return txConnection;
		}
		return targetDataSource.getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		Connection txConnection = ThreadLocalTransactionContext.getCurrentConnection();
		if (txConnection != null) {
			return txConnection;
		}
		return targetDataSource.getConnection(username, password);
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return targetDataSource.getLogWriter();
	}

	@Override
	public void setLogWriter(PrintWriter out) throws SQLException {
		targetDataSource.setLogWriter(out);
	}

	@Override
	public void setLoginTimeout(int seconds) throws SQLException {
		targetDataSource.setLoginTimeout(seconds);
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		return targetDataSource.getLoginTimeout();
	}

	@Override
	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return targetDataSource.getParentLogger();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return targetDataSource.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return targetDataSource.isWrapperFor(iface);
	}
}
