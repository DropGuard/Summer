package com.github.dropguard.summer.tx;

import com.github.dropguard.summer.aop.Interceptor;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

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
        if (chain.method().isAnnotationPresent(Transactional.class)) {
            return transactionManager.executeInTransaction(chain::proceed);
        }
        return chain.proceed();
    }
}
