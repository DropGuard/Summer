package com.github.dropguard.summer.tx;

@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction() throws Throwable;
}
