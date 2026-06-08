package summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import summer.aop.InterceptorChain;
import summer.aop.MethodMetadata;
import summer.aop.RuntimeMethodMetadata;

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
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("transactionalMethod"));
		InterceptorChain chain = new TestInterceptorChain(target, methodMetadata, new Object[0]);

		Object result = interceptor.intercept(chain);
		assertEquals("result", result);
	}

	@Test
	void shouldNotInterceptNonTransactionalMethod() throws Throwable {
		TransactionManager manager = new TestTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(
				TestService.class.getMethod("nonTransactionalMethod"));
		InterceptorChain chain = new TestInterceptorChain(target, methodMetadata, new Object[0]);

		Object result = interceptor.intercept(chain);
		assertEquals("result", result);
	}

	@Test
	void shouldRollbackOnException() throws Throwable {
		TransactionManager manager = new TestTransactionManager();
		TransactionInterceptor interceptor = new TransactionInterceptor(manager);

		TestService target = new TestServiceImpl();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("transactionalMethod"));
		InterceptorChain chain = new TestInterceptorChain(target, methodMetadata, new Object[0]) {
			@Override
			public Object proceed() throws Throwable {
				throw new RuntimeException("Test exception");
			}
		};

		assertThrows(RuntimeException.class, () -> interceptor.intercept(chain));
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

	public interface NonTransactionalService {
		String method();
	}

	// Test TransactionManager implementation
	private static class TestTransactionManager implements TransactionManager {
		@Override
		public TransactionStatus begin() {
			return new TransactionStatus() {
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
			};
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

	// Test InterceptorChain implementation
	private static class TestInterceptorChain implements InterceptorChain {
		private final Object target;
		private final MethodMetadata methodMetadata;
		private final Object[] arguments;

		TestInterceptorChain(Object target, MethodMetadata methodMetadata, Object[] arguments) {
			this.target = target;
			this.methodMetadata = methodMetadata;
			this.arguments = arguments;
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
			if (methodMetadata instanceof RuntimeMethodMetadata runtimeMetadata) {
				try {
					java.lang.reflect.Field methodField = RuntimeMethodMetadata.class.getDeclaredField("method");
					methodField.setAccessible(true);
					Method method = (Method) methodField.get(runtimeMetadata);
					method.setAccessible(true);
					return method.invoke(target, arguments);
				} catch (Exception e) {
					throw new RuntimeException("Failed to invoke method", e);
				}
			}
			throw new UnsupportedOperationException("Only RuntimeMethodMetadata is supported in tests");
		}
	}
}
