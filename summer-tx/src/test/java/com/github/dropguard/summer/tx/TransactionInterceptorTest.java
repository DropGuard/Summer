package com.github.dropguard.summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.aop.InterceptedMethod;
import com.github.dropguard.summer.aop.InterceptorChain;
import com.github.dropguard.summer.aop.TargetInvoker;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransactionInterceptor}.
 */
class TransactionInterceptorTest {

	@Test
	void shouldCreateTransactionInterceptor() {
		TransactionManager manager = new TestTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		assertNotNull(interceptor);
	}

	@Test
	void shouldInterceptTransactionalMethod() throws Throwable {
		TransactionManager manager = new TestTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		InterceptedMethod metadata = new InterceptedMethod("transactionalMethod", Set.of(Transactional.class));
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0], target::transactionalMethod);

		Object result = interceptor.intercept(chain);
		assertEquals("result", result);
	}

	@Test
	void shouldNotInterceptNonTransactionalMethod() throws Throwable {
		TransactionManager manager = new TestTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		InterceptedMethod metadata = new InterceptedMethod("nonTransactionalMethod", Set.of());
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0],
				target::nonTransactionalMethod);

		Object result = interceptor.intercept(chain);
		assertEquals("result", result);
	}

	@Test
	void shouldRollbackOnException() throws Throwable {
		TrackingTransactionManager manager = new TrackingTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		InterceptedMethod metadata = new InterceptedMethod("transactionalMethod", Set.of(Transactional.class));
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0], () -> {
			throw new RuntimeException("Test exception");
		});

		assertThrows(RuntimeException.class, () -> interceptor.intercept(chain));

		assertTrue(manager.rollbackCalled.get(), "rollback() should have been called on exception");
		assertFalse(manager.commitCalled.get(), "commit() should NOT have been called");
	}

	@Test
	void shouldCommitOnSuccess() throws Throwable {
		TrackingTransactionManager manager = new TrackingTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		InterceptedMethod metadata = new InterceptedMethod("transactionalMethod", Set.of(Transactional.class));
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0], target::transactionalMethod);

		Object result = interceptor.intercept(chain);
		assertEquals("result", result);

		assertTrue(manager.commitCalled.get(), "commit() should have been called on success");
		assertFalse(manager.rollbackCalled.get(), "rollback() should NOT have been called");
	}

	// Test interfaces and implementations
	public interface TestService {
		@Transactional
		String transactionalMethod();

		String nonTransactionalMethod();
	}

	public static class TestServiceImpl implements TestService {
		@Override
		public String transactionalMethod() {
			return "result";
		}

		@Override
		public String nonTransactionalMethod() {
			return "result";
		}
	}

	// Test TransactionManager implementation (no-op)
	private static class TestTransactionManager implements TransactionManager {
		@Override
		public TransactionStatus begin() {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
			// no-op
		}

		@Override
		public void rollback(TransactionStatus status) {
			// no-op
		}
	}

	// Tracking TransactionManager that records method calls
	private static class TrackingTransactionManager implements TransactionManager {
		final AtomicBoolean commitCalled = new AtomicBoolean(false);
		final AtomicBoolean rollbackCalled = new AtomicBoolean(false);

		@Override
		public TransactionStatus begin() {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
			commitCalled.set(true);
		}

		@Override
		public void rollback(TransactionStatus status) {
			rollbackCalled.set(true);
		}
	}

	private static class SimpleTransactionStatus implements TransactionStatus {
		private boolean active = true;
		private boolean rollbackOnly = false;

		@Override
		public boolean isActive() {
			return active;
		}

		@Override
		public boolean isNewTransaction() {
			return true;
		}

		@Override
		public boolean isRollbackOnly() {
			return rollbackOnly;
		}

		@Override
		public void setRollbackOnly() {
			this.rollbackOnly = true;
		}

		@Override
		public void flush() {
			// no-op
		}
	}

	// Test InterceptorChain implementation
	private static class TestInterceptorChain implements InterceptorChain {
		private final Object target;
		private final InterceptedMethod methodMetadata;
		private final Object[] arguments;
		private final TargetInvoker invoker;

		TestInterceptorChain(Object target, InterceptedMethod methodMetadata, Object[] arguments,
				TargetInvoker invoker) {
			this.target = target;
			this.methodMetadata = methodMetadata;
			this.arguments = arguments;
			this.invoker = invoker;
		}

		@Override
		public Object getTarget() {
			return target;
		}

		@Override
		public InterceptedMethod method() {
			return methodMetadata;
		}

		@Override
		public Object[] getArguments() {
			return arguments;
		}

		@Override
		public Object proceed() throws Throwable {
			return invoker.invoke();
		}
	}
}
