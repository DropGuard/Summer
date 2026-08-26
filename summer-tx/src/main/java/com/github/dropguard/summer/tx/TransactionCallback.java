package com.github.dropguard.summer.tx;

/**
 * Unit of work executed inside a transaction.
 *
 * <p>Declared as {@code throws Exception}: callers handle checked exceptions naturally, without
 * forcing a catch of {@code Throwable} on every call site. {@link RuntimeException} and {@code
 * Error} propagate unchanged; exotic {@code Throwable} subclasses that are neither Exception nor
 * Error cannot cross this boundary and are wrapped by the framework's invocation adapters ({@link
 * TransactionInterceptor}) into a {@link SummerTransactionException}.
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction() throws Exception;
}
