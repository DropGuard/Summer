mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.tx;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.aop.Interceptor;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.aop.InterceptorChain;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.aop.MethodInterceptor;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
@Internal
 * Transaction interceptor that manages transaction boundaries around method calls.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Bound to {@code @Transactional} via {@code @InterceptorBinding}.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is a framework infrastructure bean provided by {@link TxInfrastructureConfiguration}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
@Interceptor
mport com.github.dropguard.summer.core.Internal;
@ConditionalOnBean(TransactionManager.class)
mport com.github.dropguard.summer.core.Internal;
@Transactional
mport com.github.dropguard.summer.core.Internal;
public class TransactionInterceptor implements MethodInterceptor {
mport com.github.dropguard.summer.core.Internal;
    private static final ThreadLocal<Boolean> interceptorActive =
mport com.github.dropguard.summer.core.Internal;
            ThreadLocal.withInitial(() -> false);
mport com.github.dropguard.summer.core.Internal;
    private final TransactionManager transactionManager;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public TransactionInterceptor(TransactionManager transactionManager) {
mport com.github.dropguard.summer.core.Internal;
        this.transactionManager = transactionManager;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public static boolean isInterceptorActive() {
mport com.github.dropguard.summer.core.Internal;
        return interceptorActive.get();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object intercept(InterceptorChain chain) throws Throwable {
mport com.github.dropguard.summer.core.Internal;
        // Check if method is annotated with @Transactional
mport com.github.dropguard.summer.core.Internal;
        if (chain.method().isAnnotationPresent(Transactional.class)) {
mport com.github.dropguard.summer.core.Internal;
            boolean alreadyActive = interceptorActive.get();
mport com.github.dropguard.summer.core.Internal;
            try {
mport com.github.dropguard.summer.core.Internal;
                interceptorActive.set(true);
mport com.github.dropguard.summer.core.Internal;
                return handleTransactional(chain);
mport com.github.dropguard.summer.core.Internal;
            } finally {
mport com.github.dropguard.summer.core.Internal;
                if (!alreadyActive) {
mport com.github.dropguard.summer.core.Internal;
                    interceptorActive.remove();
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        return chain.proceed();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Object handleTransactional(InterceptorChain chain) throws Throwable {
mport com.github.dropguard.summer.core.Internal;
        TransactionStatus transaction = null;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        try {
mport com.github.dropguard.summer.core.Internal;
            transaction = transactionManager.begin();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            Object result = chain.proceed();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            if (transaction != null && !transaction.isRollbackOnly()) {
mport com.github.dropguard.summer.core.Internal;
                transactionManager.commit(transaction);
mport com.github.dropguard.summer.core.Internal;
            } else if (transaction != null) {
mport com.github.dropguard.summer.core.Internal;
                transactionManager.rollback(transaction);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            return result;
mport com.github.dropguard.summer.core.Internal;
        } catch (Exception e) {
mport com.github.dropguard.summer.core.Internal;
            if (transaction != null) {
mport com.github.dropguard.summer.core.Internal;
                transactionManager.rollback(transaction);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            throw e;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
