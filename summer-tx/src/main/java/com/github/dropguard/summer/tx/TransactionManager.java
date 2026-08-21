package com.github.dropguard.summer.tx;

public interface TransactionManager {
    <T> T executeInTransaction(TransactionCallback<T> action) throws Throwable;
}
