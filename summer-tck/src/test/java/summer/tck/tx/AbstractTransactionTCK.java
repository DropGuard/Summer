package summer.tck.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.tck.AbstractContextTCK;
import summer.fixtures.tx.dummy.TransactionalService;
import summer.fixtures.tx.dummy.TxTestConfiguration;

/**
 * TCK for transaction commit/rollback behavior.
 *
 * <p>
 * Verifies:
 * <ul>
 * <li>Transaction commits on successful execution</li>
 * <li>Transaction rolls back on exception</li>
 * </ul>
 */
public abstract class AbstractTransactionTCK extends AbstractContextTCK {

	static {
		System.setProperty("net.bytebuddy.experimental", "true");
	}

	protected TransactionalService service;
	protected Connection connection;

	@BeforeEach
	void setUpTransaction() {
		TxTestConfiguration.initMocks();
		connection = TxTestConfiguration.getMockConnection();

		// Force context initialization
		BeanContainer ctx = context();
		service = ctx.getBean(TransactionalService.class);
	}

	@Test
	void testTransactionCommitOnSuccess() throws SQLException {
		assertNotNull(service, "TransactionalService must be registered");
		service.doSuccess();

		verify(connection, times(1)).setAutoCommit(false);
		verify(connection, times(1)).commit();
		verify(connection, never()).rollback();
		verify(connection, times(1)).close();
	}

	@Test
	void testTransactionRollbackOnFailure() throws SQLException {
		assertNotNull(service, "TransactionalService must be registered");

		assertThrows(RuntimeException.class, () -> {
			service.doFailure();
		});

		verify(connection, times(1)).setAutoCommit(false);
		verify(connection, times(1)).rollback();
		verify(connection, never()).commit();
		verify(connection, times(1)).close();
	}
}
