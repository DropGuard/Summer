package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.aop.MethodMetadata;

/**
 * Tests for {@link MethodInterceptor} interface.
 */
class MethodInterceptorTest {

	@Test
	void shouldCreateMethodInterceptor() {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				return chain.proceed();
			}
		};

		assertNotNull(interceptor);
	}

	@Test
	void shouldInterceptMethodCall() throws Throwable {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				return "Intercepted: " + chain.proceed();
			}
		};

		TestService target = new TestService();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("sayHello"));
		InterceptorChain chain = new TestInterceptorChain(target, methodMetadata, new Object[0]);

		Object result = interceptor.intercept(chain);
		assertEquals("Intercepted: Hello", result);
	}

	@Test
	void shouldModifyMethodArguments() throws Throwable {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				Object[] args = chain.getArguments();
				if (args.length > 0 && args[0] instanceof String) {
					args[0] = args[0].toString().toUpperCase();
				}
				return chain.proceed();
			}
		};

		TestService target = new TestService();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("greet", String.class));
		Object[] args = new Object[]{"world"};
		InterceptorChain chain = new TestInterceptorChain(target, methodMetadata, args);

		Object result = interceptor.intercept(chain);
		assertEquals("Hello, WORLD!", result);
	}

	@Test
	void shouldHandleExceptionInInterceptor() throws Exception {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				throw new RuntimeException("Interceptor error");
			}
		};

		TestService target = new TestService();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("sayHello"));
		InterceptorChain chain = new TestInterceptorChain(target, methodMetadata, new Object[0]);

		assertThrows(RuntimeException.class, () -> interceptor.intercept(chain));
	}

	// Test helper class
	public static class TestService {
		public String sayHello() {
			return "Hello";
		}

		public String greet(String name) {
			return "Hello, " + name + "!";
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