package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.aop.SummerAopException;
import summer.fixtures.di.runtime.TestIntercepted;

/**
 * Tests for {@link ProxyFactory}.
 */
class ProxyFactoryTest {

	@Test
	void shouldCreateProxy() {
		TestService target = new TestServiceImpl();
		List<MethodInterceptor> interceptors = List.of();
		TestService proxy = ProxyFactory.createProxy(target, interceptors);

		assertNotNull(proxy);
		assertNotSame(target, proxy);
	}

	@Test
	void shouldCreateProxyWithInterceptor() {
		TestService target = new TestServiceImpl();
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				return "Intercepted: " + chain.proceed();
			}
		};

		TestService proxy = ProxyFactory.createProxy(target, List.of(interceptor));
		assertNotNull(proxy);
	}

	@Test
	void shouldThrowWhenTargetHasNoInterfaces() {
		Object target = new Object();
		List<MethodInterceptor> interceptors = List.of();

		assertThrows(SummerAopException.class, () -> ProxyFactory.createProxy(target, interceptors));
	}

	@Test
	void shouldDelegateToObjectMethods() {
		TestService target = new TestServiceImpl();
		List<MethodInterceptor> interceptors = List.of();
		TestService proxy = ProxyFactory.createProxy(target, interceptors);

		// Test toString
		assertEquals(target.toString(), proxy.toString());

		// Test hashCode
		assertEquals(target.hashCode(), proxy.hashCode());

		// Test equals
		assertTrue(proxy.equals(target));
	}

	@Test
	void shouldInterceptMethodWithAnnotation() {
		TestService target = new TestServiceImpl();
		MethodInterceptor interceptor = new TestInterceptor();

		TestService proxy = ProxyFactory.createProxy(target, List.of(interceptor));
		String result = proxy.sayHello();
		assertEquals("Intercepted: Hello", result);
	}

	@Test
	void shouldNotInterceptMethodWithoutAnnotation() {
		TestService target = new TestServiceImpl();
		// Use an interceptor without @TestIntercepted binding
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				return "Intercepted: " + chain.proceed();
			}
		};

		TestService proxy = ProxyFactory.createProxy(target, List.of(interceptor));
		// No binding annotation on interceptor, so no interception
		String result = proxy.sayHello();
		assertEquals("Hello", result);
	}

	@Test
	void shouldHandleMultipleInterceptors() {
		TestService target = new TestServiceImpl();
		MethodInterceptor interceptor1 = new FirstTestInterceptor();
		MethodInterceptor interceptor2 = new SecondTestInterceptor();

		TestService proxy = ProxyFactory.createProxy(target, List.of(interceptor1, interceptor2));
		String result = proxy.sayHello();
		// Interceptors are executed in order: interceptor1 wraps interceptor2
		assertEquals("First: Second: Hello", result);
	}

	// Test interfaces and implementations
	public interface TestService {
		@TestIntercepted
		String sayHello();

		String greet(String name);
	}

	public static class TestServiceImpl implements TestService {
		@Override
		@TestIntercepted
		public String sayHello() {
			return "Hello";
		}

		@Override
		public String greet(String name) {
			return "Hello, " + name + "!";
		}
	}

	@TestIntercepted
	public static class TestInterceptor implements MethodInterceptor {
		@Override
		public Object intercept(InterceptorChain chain) throws Throwable {
			return "Intercepted: " + chain.proceed();
		}
	}

	@TestIntercepted
	public static class FirstTestInterceptor implements MethodInterceptor {
		@Override
		public Object intercept(InterceptorChain chain) throws Throwable {
			return "First: " + chain.proceed();
		}
	}

	@TestIntercepted
	public static class SecondTestInterceptor implements MethodInterceptor {
		@Override
		public Object intercept(InterceptorChain chain) throws Throwable {
			return "Second: " + chain.proceed();
		}
	}
}
