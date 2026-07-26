package com.github.dropguard.summer.tx;

/**
 * The transaction manager interface that defines transaction operations.
 */
public interface TransactionManager {
	TransactionStatus begin();
	void commit(TransactionStatus status);
	void rollback(TransactionStatus status);
}
