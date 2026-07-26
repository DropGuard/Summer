package com.github.dropguard.summer.data.jdbc.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.tx.SummerTransactionException;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ThreadLocalTransactionContext}.
 */
class ThreadLocalTransactionContextTest {

	@AfterEach
	void cleanup() {
		ThreadLocalTransactionContext.clearCurrentConnection();
	}

	@Test
	void shouldSetCurrentConnectionForNewTransaction() throws SQLException {
		Connection conn = mock(Connection.class);
		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, true);

		Connection current = ThreadLocalTransactionContext.getCurrentConnection();
		assertInstanceOf(TransactionAwareConnectionWrapper.class, current);
		assertTrue(context.isActive());
		assertTrue(context.isNewTransaction());
		assertFalse(context.isRollbackOnly());
	}

	@Test
	void shouldNotSetCurrentConnectionForExistingTransaction() throws SQLException {
		Connection conn = mock(Connection.class);
		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, false);

		assertNull(ThreadLocalTransactionContext.getCurrentConnection());
		assertFalse(context.isNewTransaction());
	}

	@Test
	void shouldSetRollbackOnly() throws SQLException {
		Connection conn = mock(Connection.class);
		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, true);

		assertFalse(context.isRollbackOnly());
		context.setRollbackOnly();
		assertTrue(context.isRollbackOnly());
	}

	@Test
	void shouldCloseConnectionOnClose() throws SQLException {
		Connection conn = mock(Connection.class);
		when(conn.isClosed()).thenReturn(false);

		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, true);
		context.close();

		assertFalse(context.isActive());
		verify(conn).close();
		assertNull(ThreadLocalTransactionContext.getCurrentConnection());
	}

	@Test
	void shouldNotCloseConnectionForExistingTransaction() throws SQLException {
		Connection conn = mock(Connection.class);

		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, false);
		context.close();

		assertFalse(context.isActive());
		verify(conn, never()).close();
	}

	@Test
	void shouldHandleAlreadyClosedConnection() throws SQLException {
		Connection conn = mock(Connection.class);
		when(conn.isClosed()).thenReturn(true);

		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, true);
		context.close();

		verify(conn, never()).close();
	}

	@Test
	void shouldHandleCloseFailure() throws SQLException {
		Connection conn = mock(Connection.class);
		when(conn.isClosed()).thenReturn(false);
		doThrow(new SQLException("Close failed")).when(conn).close();

		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, true);

		assertThrows(SummerTransactionException.class, context::close);
	}

	@Test
	void shouldClearCurrentConnection() throws SQLException {
		Connection conn = mock(Connection.class);
		new ThreadLocalTransactionContext(conn, true);

		assertNotNull(ThreadLocalTransactionContext.getCurrentConnection());
		ThreadLocalTransactionContext.clearCurrentConnection();
		assertNull(ThreadLocalTransactionContext.getCurrentConnection());
	}

	@Test
	void shouldFlushBeNoop() throws SQLException {
		Connection conn = mock(Connection.class);
		ThreadLocalTransactionContext context = new ThreadLocalTransactionContext(conn, true);

		assertDoesNotThrow(context::flush);
	}
}
