package summer.aop;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MethodInterceptor} interface.
 */
class MethodInterceptorTest {

	@Test
	void shouldCreateMethodInterceptor() {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InvocationContext context) throws Throwable {
				return context.proceed();
			}
		};

		assertNotNull(interceptor);
		assertTrue(interceptor.supports(Object.class));
	}

	@Test
	void shouldSupportTargetClass() {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InvocationContext context) throws Throwable {
				return context.proceed();
			}

			@Override
			public boolean supports(Class<?> targetClass) {
				return targetClass == String.class;
			}
		};

		assertTrue(interceptor.supports(String.class));
		assertFalse(interceptor.supports(Integer.class));
	}

	@Test
	void shouldInterceptMethodCall() throws Throwable {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InvocationContext context) throws Throwable {
				return "Intercepted: " + context.proceed();
			}
		};

		TestService target = new TestService();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("sayHello"));
		InvocationContext context = new TestInvocationContext(target, methodMetadata, new Object[0]);

		Object result = interceptor.intercept(context);
		assertEquals("Intercepted: Hello", result);
	}

	@Test
	void shouldModifyMethodArguments() throws Throwable {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InvocationContext context) throws Throwable {
				Object[] args = context.getArguments();
				if (args.length > 0 && args[0] instanceof String) {
					args[0] = args[0].toString().toUpperCase();
				}
				return context.proceed();
			}
		};

		TestService target = new TestService();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("greet", String.class));
		InvocationContext context = new TestInvocationContext(target, methodMetadata, new Object[]{"world"});

		Object result = interceptor.intercept(context);
		assertEquals("Hello, WORLD!", result);
	}

	@Test
	void shouldHandleExceptionInInterceptor() throws Exception {
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InvocationContext context) throws Throwable {
				throw new RuntimeException("Interceptor error");
			}
		};

		TestService target = new TestService();
		MethodMetadata methodMetadata = new RuntimeMethodMetadata(TestService.class.getMethod("sayHello"));
		InvocationContext context = new TestInvocationContext(target, methodMetadata, new Object[0]);

		assertThrows(RuntimeException.class, () -> interceptor.intercept(context));
	}

	@Test
	void shouldSupportDefaultSupportsMethod() {
		MethodInterceptor interceptor = context -> context.proceed();

		assertTrue(interceptor.supports(Object.class));
		assertTrue(interceptor.supports(String.class));
		assertTrue(interceptor.supports(Integer.class));
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

	// Test InvocationContext implementation
	private static class TestInvocationContext implements InvocationContext {
		private final Object target;
		private final MethodMetadata methodMetadata;
		private final Object[] arguments;

		TestInvocationContext(Object target, MethodMetadata methodMetadata, Object[] arguments) {
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
					// Use reflection to access the private method field
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