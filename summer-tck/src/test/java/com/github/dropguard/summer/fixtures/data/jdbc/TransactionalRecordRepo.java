package com.github.dropguard.summer.fixtures.data.jdbc;

/**
 * Positive fixture contract: a transactional repository that implements an interface, so JDK
 * dynamic proxying applies and {@code @Transactional} is honoured. {@link
 * TransactionalRecordRepoImpl} is the concrete bean.
 */
public interface TransactionalRecordRepo {
    void insertThenFail(Long id);

    /** Checked-failure variant: the contract is that ANY thrown exception rolls back. */
    void insertThenFailChecked(Long id) throws java.io.IOException;

    boolean exists(Long id);
}
