package summer.tx;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Proxy for a target DataSource that intercepts getConnection() to return the
 * current thread-bound transactional connection if one exists. This allows
 * third-party ORMs (like MyBatis or JDBI) to participate in
 * Summer's @Transactional mechanism.
 */
public class TransactionAwareDataSourceProxy implements DataSource {

	private final DataSource targetDataSource;

	public TransactionAwareDataSourceProxy(DataSource targetDataSource) {
		this.targetDataSource = targetDataSource;
	}

	/**
	 * Returns the transactional connection if one exists, wrapped to ignore close()
	 * calls. Otherwise returns a fresh connection from the target DataSource.
	 */
	@Override
	public Connection getConnection() throws SQLException {
		Connection txConnection = ThreadLocalTransactionContext.getCurrentConnection();
		if (txConnection != null) {
			return new TransactionAwareConnectionWrapper(txConnection);
		}
		return targetDataSource.getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		Connection txConnection = ThreadLocalTransactionContext.getCurrentConnection();
		if (txConnection != null) {
			return new TransactionAwareConnectionWrapper(txConnection);
		}
		return targetDataSource.getConnection(username, password);
	}

	// Delegate remaining DataSource methods to the target
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
