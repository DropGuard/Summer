package com.github.dropguard.summer.tx;

/**
 * Transaction SPI. Implementations (e.g. the JDBC-backed manager) must roll back on any {@code
 * Throwable} escaping the callback — including {@code Error} — and rethrow it unchanged.
 *
 * <p>The checked signature is {@code throws Exception} (narrowed from the original {@code throws
 * Throwable}, which forced every caller into a catch-all): see {@link TransactionCallback} for how
 * exotic throwable types are handled.
 */
public interface TransactionManager {
    <T> T executeInTransaction(TransactionCallback<T> action) throws Exception;
}
