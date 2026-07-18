package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import summer.aop.InterceptedMethod;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.aop.TargetInvoker;

/**
 * Tests for {@link MethodInterceptor} interface.
 */
class MethodInterceptorTest {

	@Test
	void shouldInterceptMethodCall() throws Throwable {
		MethodInterceptor interceptor = chain -> "Intercepted: " + chain.proceed();

		TestService target = new TestService();
		InterceptedMethod metadata = new InterceptedMethod("sayHello", Set.of());
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0], target::sayHello);

		Object result = interceptor.intercept(chain);
		assertEquals("Intercepted: Hello", result);
	}

	@Test
	void shouldModifyMethodArguments() throws Throwable {
		MethodInterceptor interceptor = chain -> {
			Object[] args = chain.getArguments();
			if (args.length > 0 && args[0] instanceof String s) {
				args[0] = s.toUpperCase();
			}
			return chain.proceed();
		};

		TestService target = new TestService();
		InterceptedMethod metadata = new InterceptedMethod("greet", Set.of());
		Object[] args = {"world"};
		InterceptorChain chain = new TestInterceptorChain(target, metadata, args, () -> target.greet((String) args[0]));

		Object result = interceptor.intercept(chain);
		assertEquals("Hello, WORLD!", result);
	}

	@Test
	void shouldHandleExceptionInInterceptor() throws Exception {
		MethodInterceptor interceptor = chain -> {
			throw new RuntimeException("Interceptor error");
		};

		TestService target = new TestService();
		InterceptedMethod metadata = new InterceptedMethod("sayHello", Set.of());
		InterceptorChain chain = new TestInterceptorChain(target, metadata, new Object[0], target::sayHello);

		assertThrows(RuntimeException.class, () -> interceptor.intercept(chain));
	}

	public static class TestService {
		public String sayHello() {
			return "Hello";
		}

		public String greet(String name) {
			return "Hello, " + name + "!";
		}
	}

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
