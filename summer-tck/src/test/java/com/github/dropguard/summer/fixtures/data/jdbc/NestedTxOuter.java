package com.github.dropguard.summer.fixtures.data.jdbc;

/**
 * Outer half of the nested-transaction contract: writes inside its own transaction, then calls the
 * inner bean's {@code @Transactional} method THROUGH ITS PROXY (constructor-injected), so the
 * nested {@code begin()} must fail loudly and this outer boundary must roll back.
 */
public interface NestedTxOuter {

    void insertWithNested(Long id);

    boolean exists(Long id);
}
