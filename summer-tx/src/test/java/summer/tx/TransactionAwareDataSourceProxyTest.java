package summer.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransactionAwareDataSourceProxy}.
 *
 * <p>
 * Only tests the core behavior: returning transactional connection when one
 * exists, or delegating to target DataSource when no transaction is active.
 * </p>
 */
class TransactionAwareDataSourceProxyTest {

	@AfterEach
	void cleanup() {
		ThreadLocalTransactionContext.clearCurrentConnection();
	}

	@Test
	void shouldReturnTransactionalConnectionWhenExists() throws SQLException {
		DataSource targetDs = mock(DataSource.class);
		Connection txConn = mock(Connection.class);

		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(txConn, true);

		TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(targetDs);
		Connection result = proxy.getConnection();

		assertInstanceOf(TransactionAwareConnectionWrapper.class, result);
		verify(targetDs, never()).getConnection();
	}

	@Test
	void shouldReturnFreshConnectionWhenNoTransaction() throws SQLException {
		DataSource targetDs = mock(DataSource.class);
		Connection freshConn = mock(Connection.class);
		when(targetDs.getConnection()).thenReturn(freshConn);

		TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(targetDs);
		Connection result = proxy.getConnection();

		assertSame(freshConn, result);
	}
}
