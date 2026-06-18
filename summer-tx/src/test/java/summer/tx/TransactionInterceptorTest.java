package summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import summer.aop.InterceptorChain;
import summer.aop.MethodMetadata;
import summer.aop.TargetInvoker;

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
		MethodMetadata metadata = new SimpleMethodMetadata(TestService.class.getMethod("transactionalMethod"));
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0], target::transactionalMethod);

		Object result = interceptor.intercept(chain);
		assertEquals("result", result);
	}

	@Test
	void shouldNotInterceptNonTransactionalMethod() throws Throwable {
		TransactionManager manager = new TestTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		MethodMetadata metadata = new SimpleMethodMetadata(TestService.class.getMethod("nonTransactionalMethod"));
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
		MethodMetadata metadata = new SimpleMethodMetadata(TestService.class.getMethod("transactionalMethod"));
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
		MethodMetadata metadata = new SimpleMethodMetadata(TestService.class.getMethod("transactionalMethod"));
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
		private final MethodMetadata methodMetadata;
		private final Object[] arguments;
		private final TargetInvoker invoker;

		TestInterceptorChain(Object target, MethodMetadata methodMetadata, Object[] arguments, TargetInvoker invoker) {
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
		public MethodMetadata getMethod() {
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

	private record SimpleMethodMetadata(Method method) implements MethodMetadata {
		@Override
		public String getName() {
			return method.getName();
		}

		@Override
		public Class<?> getDeclaringClass() {
			return method.getDeclaringClass();
		}

		@Override
		public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
			return method.isAnnotationPresent(annotationClass);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return method.getAnnotation(annotationClass);
		}
	}
}
