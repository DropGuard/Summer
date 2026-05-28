package summer.tck.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import summer.core.ApplicationContext;
import summer.tck.tx.dummy.TransactionalService;
import summer.tck.tx.dummy.TxTestConfiguration;

public abstract class AbstractTransactionTCK {

	static {
		System.setProperty("net.bytebuddy.experimental", "true");
	}

	protected ApplicationContext context;
	protected TransactionalService service;
	protected Connection connection;

	protected abstract ApplicationContext createAndInitializeContext();

	@BeforeEach
	void setUp() {
		TxTestConfiguration.initMocks();
		connection = TxTestConfiguration.getMockConnection();

		context = createAndInitializeContext();
		service = context.getBean(TransactionalService.class);
	}

	@AfterEach
	void tearDown() {
		if (context != null) {
			context.destroy();
			context = null;
		}
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
