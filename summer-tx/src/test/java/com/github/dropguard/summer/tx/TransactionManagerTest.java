package com.github.dropguard.summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Tests for {@link TransactionManager} interface. */
class TransactionManagerTest {

    @Test
    void shouldCreateTransactionManager() {
        TransactionManager manager = new TestTransactionManager();
        assertNotNull(manager);
    }

    @Test
    void shouldBeginTransaction() {
        TransactionManager manager = new TestTransactionManager();
        TransactionStatus status = manager.begin();

        assertNotNull(status);
        assertTrue(status.isActive());
        assertFalse(status.isRollbackOnly());
    }

    @Test
    void shouldCommitTransaction() {
        TransactionManager manager = new TestTransactionManager();
        TransactionStatus status = manager.begin();

        manager.commit(status);

        // After commit, transaction should no longer be active
        assertFalse(status.isActive());
    }

    @Test
    void shouldRollbackTransaction() {
        TransactionManager manager = new TestTransactionManager();
        TransactionStatus status = manager.begin();

        manager.rollback(status);

        // After rollback, transaction should no longer be active
        assertFalse(status.isActive());
    }

    @Test
    void shouldSetRollbackOnly() {
        TransactionManager manager = new TestTransactionManager();
        TransactionStatus status = manager.begin();

        status.setRollbackOnly();

        assertTrue(status.isRollbackOnly());
    }

    @Test
    void shouldHandleNewTransaction() {
        TransactionManager manager = new TestTransactionManager();
        TransactionStatus status = manager.begin();

        assertTrue(status.isNewTransaction());
    }

    // Test helper class
    private static class TestTransactionManager implements TransactionManager {
        @Override
        public TransactionStatus begin() {
            return new TestTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            ((TestTransactionStatus) status).setActive(false);
        }

        @Override
        public void rollback(TransactionStatus status) {
            ((TestTransactionStatus) status).setActive(false);
        }
    }

    private static class TestTransactionStatus implements TransactionStatus {
        private boolean active = true;
        private boolean rollbackOnly = false;

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public void setRollbackOnly() {
            this.rollbackOnly = true;
        }

        void setActive(boolean active) {
            this.active = active;
        }
    }
}
