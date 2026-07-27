package com.github.dropguard.summer.data.jdbc.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.tx.SummerTransactionException;
import com.github.dropguard.summer.tx.TransactionStatus;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SimpleJdbcTransactionManager}. */
class SimpleJdbcTransactionManagerTest {

    @AfterEach
    void cleanup() {
        ThreadLocalTransactionContext.clearCurrentConnection();
    }

    @Test
    void shouldBeginTransaction() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);
        TransactionStatus status = manager.begin();

        assertNotNull(status);
        verify(conn).setAutoCommit(false);
    }

    @Test
    void shouldThrowOnNestedTransaction() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);
        manager.begin(); // First transaction

        assertThrows(SummerTransactionException.class, manager::begin);
    }

    @Test
    void shouldCommitTransaction() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(false);

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);
        TransactionStatus status = manager.begin();
        manager.commit(status);

        verify(conn).commit();
        verify(conn).close();
    }

    @Test
    void shouldRollbackTransaction() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(false);

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);
        TransactionStatus status = manager.begin();
        manager.rollback(status);

        verify(conn).rollback();
        verify(conn).close();
    }

    @Test
    void shouldHandleCommitFailure() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("Commit failed")).when(conn).commit();

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);
        TransactionStatus status = manager.begin();

        assertThrows(SummerTransactionException.class, () -> manager.commit(status));
        verify(conn).rollback();
    }

    @Test
    void shouldHandleRollbackFailure() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("Rollback failed")).when(conn).rollback();

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);
        TransactionStatus status = manager.begin();

        assertThrows(SummerTransactionException.class, () -> manager.rollback(status));
    }

    @Test
    void shouldHandleBeginFailure() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("Connection failed"));

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);

        assertThrows(SummerTransactionException.class, manager::begin);
    }

    @Test
    void shouldCleanupConnectionOnBeginFailure() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        doThrow(new SQLException("setAutoCommit failed")).when(conn).setAutoCommit(false);

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(ds);

        assertThrows(SummerTransactionException.class, manager::begin);
        verify(conn).close();
    }
}
