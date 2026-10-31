package com.github.dropguard.summer.fixtures.data.jdbc;

/**
 * Inner half of the nested-transaction contract: a {@code @Transactional} method on a SEPARATE
 * bean, invoked through its proxy while the caller's transaction is active. Internal {@code
 * this.method()} calls bypass the proxy (interface-based AOP), so nesting is only reachable
 * cross-bean — which is exactly the shape {@code Transactional}'s javadoc declares unsupported.
 */
public interface NestedTxInner {

    void insert(Long id);
}
