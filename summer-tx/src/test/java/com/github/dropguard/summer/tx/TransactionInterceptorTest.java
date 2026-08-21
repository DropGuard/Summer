package com.github.dropguard.summer.tx;

import com.github.dropguard.summer.aop.InterceptedMethod;
import com.github.dropguard.summer.aop.InterceptorChain;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TransactionInterceptorTest {

    @Test
    void interceptNonTransactionalMethodBypassesManager() throws Throwable {
        TestTransactionManager manager = new TestTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor(manager);
        TestService target = new TestServiceImpl();
        InterceptedMethod method = new InterceptedMethod("nonTransactionalMethod", Set.of());
        TestInterceptorChain chain = new TestInterceptorChain(target, method, target::nonTransactionalMethod);

        Object result = interceptor.intercept(chain);

        assertEquals("result", result);
        assertFalse(manager.executed.get(), "TransactionManager should not be called for non-transactional method");
    }

    @Test
    void interceptTransactionalMethodDelegatesToManager() throws Throwable {
        TestTransactionManager manager = new TestTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor(manager);
        TestService target = new TestServiceImpl();
        InterceptedMethod method = new InterceptedMethod("transactionalMethod", Set.of(Transactional.class));
        TestInterceptorChain chain = new TestInterceptorChain(target, method, target::transactionalMethod);

        Object result = interceptor.intercept(chain);

        assertEquals("result", result);
        assertTrue(manager.executed.get(), "TransactionManager should be called for transactional method");
    }

    public interface TestService {
        @Transactional
        String transactionalMethod();
        String nonTransactionalMethod();
    }

    public static class TestServiceImpl implements TestService {
        @Override
        public String transactionalMethod() { return "result"; }
        @Override
        public String nonTransactionalMethod() { return "result"; }
    }

    private static class TestTransactionManager implements TransactionManager {
        final AtomicBoolean executed = new AtomicBoolean(false);
        @Override
        public <T> T executeInTransaction(TransactionCallback<T> action) throws Throwable {
            executed.set(true);
            return action.doInTransaction();
        }
    }

    private static class TestInterceptorChain implements InterceptorChain {
        private final Object target;
        private final InterceptedMethod methodMetadata;
        private final TargetInvoker invoker;

        TestInterceptorChain(Object target, InterceptedMethod methodMetadata, TargetInvoker invoker) {
            this.target = target;
            this.methodMetadata = methodMetadata;
            this.invoker = invoker;
        }

        @Override public Object getTarget() { return target; }
        @Override public InterceptedMethod method() { return methodMetadata; }
        @Override public Object[] getArguments() { return new Object[0]; }
        @Override public Object proceed() throws Throwable { return invoker.invoke(); }
    }

    interface TargetInvoker { Object invoke() throws Throwable; }
}
