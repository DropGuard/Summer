package com.github.dropguard.summer.data.jdbc.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.tx.SummerTransactionException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SimpleJdbcTransactionManagerTest {

    @Test
    void executeInTransactionManagesConnection() throws Throwable {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(dataSource);

        manager.executeInTransaction(
                () -> {
                    assertTrue(ScopedValueTransactionContext.CONNECTION.isBound());
                    assertNotNull(ScopedValueTransactionContext.CONNECTION.get());
                    return null;
                });

        assertFalse(ScopedValueTransactionContext.CONNECTION.isBound());
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection).close();
    }

    @Test
    void commitFailureAttemptsRollbackBeforeFailing() throws Throwable {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        doThrow(new SQLException("commit lost")).when(connection).commit();

        SimpleJdbcTransactionManager manager = new SimpleJdbcTransactionManager(dataSource);

        // The javadoc-promised rollback semantics close at the commit boundary too: an
        // unrolled-back failed commit leaves the transaction's fate to the driver/pool
        // (undefined for a bare DataSource).
        assertThrows(
                SummerTransactionException.class, () -> manager.executeInTransaction(() -> null));
        verify(connection).rollback();
        verify(connection).close();
    }
}
