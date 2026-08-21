package com.github.dropguard.summer.data.jdbc.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
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
}
