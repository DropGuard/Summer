package com.github.dropguard.summer.data.jdbc.tx;

import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
import com.github.dropguard.summer.tx.SummerTransactionException;
import com.github.dropguard.summer.tx.TransactionCallback;
import com.github.dropguard.summer.tx.TransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

@ConditionalOnBean(DataSource.class)
public class SimpleJdbcTransactionManager implements TransactionManager {
    private final DataSource dataSource;

    public SimpleJdbcTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T executeInTransaction(TransactionCallback<T> action) throws Exception {
        if (ScopedValueTransactionContext.CONNECTION.isBound()) {
            throw new SummerTransactionException(
                    "Nested transactions are not supported. A transaction is already active.");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            Connection wrapped = new TransactionAwareConnectionWrapper(connection);

            try {
                T result =
                        ScopedValue.where(ScopedValueTransactionContext.CONNECTION, wrapped)
                                .call(() -> action.doInTransaction());
                connection.commit();
                return result;
            } catch (Throwable t) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    t.addSuppressed(rollbackEx);
                }
                throw t;
            }
        } catch (SQLException e) {
            throw new SummerTransactionException("Transaction failed", e);
        }
    }
}
