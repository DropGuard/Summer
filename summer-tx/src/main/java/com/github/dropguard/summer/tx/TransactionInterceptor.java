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
        if (!chain.method().isAnnotationPresent(Transactional.class)) {
            return chain.proceed();
        }
        // chain.proceed() declares `throws Throwable` (the generic interception contract), but the
        // transaction callback is narrowed to Exception. Adapter: pass RuntimeException, Error and
        // checked Exceptions through unchanged; only exotic Throwable subclasses — neither
        // Exception nor Error, i.e. user-defined throwable types — get wrapped.
        return transactionManager.executeInTransaction(
                () -> {
                    try {
                        return chain.proceed();
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Exception e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new SummerTransactionException(
                                "Non-exception throwable escaped transactional invocation: "
                                        + t.getClass().getName(),
                                t);
                    }
                });
    }
}
