package com.github.dropguard.summer.data.jdbc.tx;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransactionAwareConnectionWrapper}.
 *
 * <p>Only tests the core behavior: suppressing close/commit/rollback. Delegation of other methods
 * is boilerplate and not tested.
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

    // ── delegated methods (jacoco: the wrapper's pass-through surface was 90% uncovered) ──

    @Test
    void delegatesStatementAndMetadataCalls() throws SQLException {
        Connection target = mock(Connection.class);
        TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

        wrapper.createStatement();
        wrapper.prepareStatement("select 1");
        wrapper.prepareCall("call x()");
        wrapper.nativeSQL("select 1");
        wrapper.getMetaData();

        verify(target).createStatement();
        verify(target).prepareStatement("select 1");
        verify(target).prepareCall("call x()");
        verify(target).nativeSQL("select 1");
        verify(target).getMetaData();
    }

    @Test
    void delegatesConnectionStateCalls() throws SQLException {
        Connection target = mock(Connection.class);
        TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

        wrapper.setAutoCommit(false);
        wrapper.getAutoCommit();
        wrapper.isClosed();
        wrapper.setReadOnly(true);
        wrapper.isReadOnly();
        wrapper.setCatalog("catalog");
        wrapper.getCatalog();
        wrapper.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        wrapper.getTransactionIsolation();
        wrapper.getWarnings();
        wrapper.clearWarnings();

        verify(target).setAutoCommit(false);
        verify(target).getAutoCommit();
        verify(target).isClosed();
        verify(target).setReadOnly(true);
        verify(target).isReadOnly();
        verify(target).setCatalog("catalog");
        verify(target).getCatalog();
        verify(target).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        verify(target).getTransactionIsolation();
        verify(target).getWarnings();
        verify(target).clearWarnings();
    }

    @Test
    void delegatesOverloadedFactories() throws SQLException {
        Connection target = mock(Connection.class);
        TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

        wrapper.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        wrapper.prepareStatement(
                "select 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        wrapper.prepareCall("call x()", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        wrapper.createStatement(0, 0, ResultSet.HOLD_CURSORS_OVER_COMMIT);
        wrapper.prepareStatement("select 1", Statement.RETURN_GENERATED_KEYS);
        wrapper.prepareStatement("select 1", new int[] {1});
        wrapper.prepareStatement("select 1", new String[] {"id"});

        verify(target).createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        verify(target)
                .prepareStatement(
                        "select 1", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        verify(target)
                .prepareCall("call x()", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        verify(target).createStatement(0, 0, ResultSet.HOLD_CURSORS_OVER_COMMIT);
        verify(target).prepareStatement("select 1", Statement.RETURN_GENERATED_KEYS);
        verify(target).prepareStatement("select 1", new int[] {1});
        verify(target).prepareStatement("select 1", new String[] {"id"});
    }

    @Test
    void delegatesSavepointAndTypeCalls() throws SQLException {
        Connection target = mock(Connection.class);
        TransactionAwareConnectionWrapper wrapper = new TransactionAwareConnectionWrapper(target);

        wrapper.setSavepoint();
        wrapper.setSavepoint("sp");
        wrapper.releaseSavepoint(mock(Savepoint.class));
        wrapper.getTypeMap();
        wrapper.setTypeMap(java.util.Map.of());
        wrapper.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        wrapper.getHoldability();
        wrapper.createClob();
        wrapper.createBlob();
        wrapper.createNClob();
        wrapper.createSQLXML();
        wrapper.isValid(5);
        wrapper.setClientInfo("name", "value");
        wrapper.getClientInfo("name");
        wrapper.getClientInfo();
        wrapper.createArrayOf("int", new Object[] {1});
        wrapper.createStruct("s", new Object[] {1});
        wrapper.setSchema("schema");
        wrapper.getSchema();
        wrapper.abort(Runnable::run);
        wrapper.setNetworkTimeout(Runnable::run, 1000);
        wrapper.getNetworkTimeout();
        wrapper.unwrap(Connection.class);
        wrapper.isWrapperFor(Connection.class);

        verify(target).setSavepoint();
        verify(target).setSavepoint("sp");
        verify(target).releaseSavepoint(any(Savepoint.class));
        verify(target).getTypeMap();
        verify(target).setTypeMap(java.util.Map.of());
        verify(target).setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
        verify(target).getHoldability();
        verify(target).createClob();
        verify(target).createBlob();
        verify(target).createNClob();
        verify(target).createSQLXML();
        verify(target).isValid(5);
        verify(target).setClientInfo("name", "value");
        verify(target).getClientInfo("name");
        verify(target).getClientInfo();
        verify(target).createArrayOf("int", new Object[] {1});
        verify(target).createStruct("s", new Object[] {1});
        verify(target).setSchema("schema");
        verify(target).getSchema();
        verify(target).abort(any());
        verify(target).setNetworkTimeout(any(), eq(1000));
        verify(target).getNetworkTimeout();
        verify(target).unwrap(Connection.class);
        verify(target).isWrapperFor(Connection.class);
    }
}
