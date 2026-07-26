package com.github.dropguard.summer.data.jdbc.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransactionAwareConnectionWrapper}.
 *
 * <p>
 * Only tests the core behavior: suppressing close/commit/rollback. Delegation
 * of other methods is boilerplate and not tested.
 * </p>
 */
class TransactionAwareConnectionWrapperTest {

	@Test
	void shouldSuppressClose() throws SQLException {
		Connection target = mock(Connection.class);
		TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

		wrapper.close();
		verify(target, never()).close();
	}

	@Test
	void shouldSuppressCommit() throws SQLException {
		Connection target = mock(Connection.class);
		TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

		wrapper.commit();
		verify(target, never()).commit();
	}

	@Test
	void shouldSuppressRollback() throws SQLException {
		Connection target = mock(Connection.class);
		TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

		wrapper.rollback();
		verify(target, never()).rollback();
	}

	@Test
	void shouldSuppressRollbackWithSavepoint() throws SQLException {
		Connection target = mock(Connection.class);
		Savepoint savepoint = mock(Savepoint.class);
		TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

		wrapper.rollback(savepoint);
		verify(target, never()).rollback(any(Savepoint.class));
	}
}
