package com.github.dropguard.summer.tx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionManagerTest {
    @Test
    void executeInTransactionCallsCallback() throws Throwable {
        TransactionManager manager = new TestTransactionManager();
        String result = manager.executeInTransaction(() -> "success");
        assertEquals("success", result);
    }

    private static class TestTransactionManager implements TransactionManager {
        @Override
        public <T> T executeInTransaction(TransactionCallback<T> action) throws Throwable {
            return action.doInTransaction();
        }
    }
}
