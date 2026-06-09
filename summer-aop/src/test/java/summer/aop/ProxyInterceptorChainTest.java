package summer.aop;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProxyInterceptorChain}.
 */
class ProxyInterceptorChainTest {

	@Test
	void shouldReturnTarget() {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {"arg1"};
		List<MethodInterceptor> interceptors = Collections.emptyList();
		TargetInvoker invoker = () -> "result";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		assertSame(target, chain.getTarget());
	}

	@Test
	void shouldReturnMethod() {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};
		List<MethodInterceptor> interceptors = Collections.emptyList();
		TargetInvoker invoker = () -> "result";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		assertSame(metadata, chain.getMethod());
	}

	@Test
	void shouldReturnArguments() {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {"arg1", "arg2"};
		List<MethodInterceptor> interceptors = Collections.emptyList();
		TargetInvoker invoker = () -> "result";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		assertSame(args, chain.getArguments());
	}

	@Test
	void shouldInvokeTargetWhenNoInterceptors() throws Throwable {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};
		List<MethodInterceptor> interceptors = Collections.emptyList();
		TargetInvoker invoker = () -> "targetResult";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		Object result = chain.proceed();
		assertEquals("targetResult", result);
	}

	@Test
	void shouldExecuteSingleInterceptor() throws Throwable {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};
		MethodInterceptor interceptor = chain -> "intercepted";
		List<MethodInterceptor> interceptors = List.of(interceptor);
		TargetInvoker invoker = () -> "targetResult";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		Object result = chain.proceed();
		assertEquals("intercepted", result);
	}

	@Test
	void shouldExecuteMultipleInterceptorsInOrder() throws Throwable {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};

		StringBuilder order = new StringBuilder();
		MethodInterceptor first = chain -> {
			order.append("1");
			return chain.proceed();
		};
		MethodInterceptor second = chain -> {
			order.append("2");
			return chain.proceed();
		};
		List<MethodInterceptor> interceptors = List.of(first, second);
		TargetInvoker invoker = () -> {
			order.append("3");
			return "targetResult";
		};

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		Object result = chain.proceed();
		assertEquals("targetResult", result);
		assertEquals("123", order.toString());
	}

	@Test
	void shouldAllowInterceptorToShortCircuit() throws Throwable {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};
		MethodInterceptor interceptor = chain -> "shortCircuit";
		List<MethodInterceptor> interceptors = List.of(interceptor);
		TargetInvoker invoker = () -> {
			fail("Target should not be invoked");
			return "targetResult";
		};

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		Object result = chain.proceed();
		assertEquals("shortCircuit", result);
	}

	@Test
	void shouldAllowInterceptorToModifyArguments() throws Throwable {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {"original"};
		MethodInterceptor interceptor = chain -> {
			Object[] modifiedArgs = chain.getArguments();
			modifiedArgs[0] = "modified";
			return chain.proceed();
		};
		List<MethodInterceptor> interceptors = List.of(interceptor);
		TargetInvoker invoker = () -> "targetResult";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		Object result = chain.proceed();
		assertEquals("targetResult", result);
		assertEquals("modified", args[0]);
	}

	@Test
	void shouldPropagateExceptionFromInterceptor() {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};
		MethodInterceptor interceptor = chain -> {
			throw new RuntimeException("Interceptor error");
		};
		List<MethodInterceptor> interceptors = List.of(interceptor);
		TargetInvoker invoker = () -> "targetResult";

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		assertThrows(RuntimeException.class, chain::proceed);
	}

	@Test
	void shouldPropagateExceptionFromTarget() {
		Object target = new Object();
		MethodMetadata metadata = new TestMethodMetadata("test");
		Object[] args = {};
		List<MethodInterceptor> interceptors = Collections.emptyList();
		TargetInvoker invoker = () -> {
			throw new RuntimeException("Target error");
		};

		ProxyInterceptorChain chain = new ProxyInterceptorChain(target, metadata, args, interceptors, invoker);

		assertThrows(RuntimeException.class, chain::proceed);
	}

	// Test helper class
	private static class TestMethodMetadata implements MethodMetadata {
		private final String name;

		TestMethodMetadata(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Class<?> getDeclaringClass() {
			return Object.class;
		}

		@Override
		public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
			return false;
		}

		@Override
		public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
			return null;
		}
	}
}
