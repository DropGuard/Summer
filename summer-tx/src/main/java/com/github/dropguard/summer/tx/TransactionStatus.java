package com.github.dropguard.summer.tx;

/**
 * Transaction status interface that represents the state of a transaction.
 *
 * <p>Deliberately minimal: status + rollback flags only. There is no flush concept in Summer's
 * transaction model — the JDBC manager writes through the connection and commit/rollback are the
 * only boundaries.
 */
public interface TransactionStatus {
    boolean isActive();

    boolean isNewTransaction();

    boolean isRollbackOnly();

    void setRollbackOnly();
}
