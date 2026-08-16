package com.github.dropguard.summer.tx;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/**
 * Transaction interceptor that manages transaction boundaries around method calls.
 *
 * <p>Bound to {@code @Transactional} via {@code @InterceptorBinding}.
 *
 * <p>Nested {@code @Transactional} calls are intentionally unsupported: a proxied
 * {@code @Transactional} method invoked from inside an active transaction fails loudly when its
 * {@code begin()} hits the manager's nesting guard (see {@link Transactional}).
 *
 * <p>This is a framework infrastructure bean provided by {@link TxInfrastructureConfiguration}.
 */
@Interceptor
@ConditionalOnBean(TransactionManager.class)
@Transactional
@Internal
public class TransactionInterceptor implements MethodInterceptor {
    private final TransactionManager transactionManager;

    public TransactionInterceptor(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object intercept(InterceptorChain chain) throws Throwable {
        // A @Transactional method opens a transaction boundary. A nested call — a proxied
        // @Transactional method invoked while a transaction is already active on this thread —
        // must NOT be silently joined: the nested begin() fails loudly in the manager, and the
        // outer boundary rolls back. (Same-bean internal this.method() calls bypass the proxy and
        // never reach here.)
        if (chain.method().isAnnotationPresent(Transactional.class)) {
            return handleTransactional(chain);
        }
        return chain.proceed();
    }

    private Object handleTransactional(InterceptorChain chain) throws Throwable {
        TransactionStatus transaction = transactionManager.begin();
        boolean committed = false;
        try {
            Object result = chain.proceed();

            if (!transaction.isRollbackOnly()) {
                transactionManager.commit(transaction);
                committed = true;
            } else {
                transactionManager.rollback(transaction);
            }
            return result;
        } catch (Throwable t) {
            // Catch Throwable (not just Exception): a method that throws an Error (OOM,
            // StackOverflowError, AssertionError, ...) must still roll the transaction back and
            // release its connection — otherwise the ThreadLocal connection leaks and the next
            // begin() on this thread throws "Nested transactions are not supported".
            if (!committed && transaction.isActive()) {
                try {
                    transactionManager.rollback(transaction);
                } catch (Throwable rollbackEx) {
                    // A rollback failure must not mask the original error/exception.
                    t.addSuppressed(rollbackEx);
                }
            }
            throw t;
        }
    }
}
