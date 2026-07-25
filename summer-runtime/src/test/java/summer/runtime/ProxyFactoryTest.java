package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.aop.SummerAopException;
import summer.fixtures.di.runtime.TestIntercepted;

/**
 * Tests for {@link ProxyFactory}.
 *
 * <p>
 * The factory is a pure dispatch table: the caller (RuntimeAopProcessor)
 * decides, per interface method, which interceptors wrap it and which binding
 * annotation types the method carries. These tests therefore supply an explicit
 * {@code Map<Method, ProxyMethodSpec>} rather than expecting the factory to
 * rediscover bindings by reflection.
 * </p>
 */
class ProxyFactoryTest {

	private static ProxyFactory.ProxyMethodSpec spec(MethodInterceptor interceptor,
			Class<? extends Annotation>... bindings) {
		Set<Class<? extends Annotation>> set = new java.util.HashSet<>();
		java.util.Collections.addAll(set, bindings);
		return new ProxyFactory.ProxyMethodSpec(List.of(interceptor), set);
	}

	private static ProxyFactory.ProxyMethodSpec spec(List<MethodInterceptor> interceptors,
			Class<? extends Annotation>... bindings) {
		Set<Class<? extends Annotation>> set = new java.util.HashSet<>();
		java.util.Collections.addAll(set, bindings);
		return new ProxyFactory.ProxyMethodSpec(interceptors, set);
	}

	private static Method method(Class<?> iface, String name, Class<?>... params) {
		try {
			return iface.getMethod(name, params);
		} catch (NoSuchMethodException e) {
			throw new AssertionError(e);
		}
	}

	private static Map<Method, ProxyFactory.ProxyMethodSpec> emptyPlan() {
		return Map.of();
	}

	@Test
	void shouldCreateProxy() {
		TestService target = new TestServiceImpl();
		TestService proxy = ProxyFactory.createProxy(target, emptyPlan());

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

		// An empty plan still yields a valid proxy (no methods wrapped).
		TestService proxy = ProxyFactory.createProxy(target, emptyPlan());
		assertNotNull(proxy);
	}

	@Test
	void shouldThrowWhenTargetHasNoInterfaces() {
		Object target = new Object();
		assertThrows(SummerAopException.class, () -> ProxyFactory.createProxy(target, emptyPlan()));
	}

	@Test
	void shouldDelegateToObjectMethods() {
		TestService target = new TestServiceImpl();
		TestService proxy = ProxyFactory.createProxy(target, emptyPlan());

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

		Method sayHello = method(TestService.class, "sayHello");
		TestService proxy = ProxyFactory.createProxy(target,
				Map.of(sayHello, spec(interceptor, TestIntercepted.class)));
		String result = proxy.sayHello();
		assertEquals("Intercepted: Hello", result);
	}

	@Test
	void shouldNotInterceptMethodWithoutSpec() {
		TestService target = new TestServiceImpl();
		// A method absent from the plan passes straight through, even if an
		// interceptor exists elsewhere.
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(InterceptorChain chain) throws Throwable {
				return "Intercepted: " + chain.proceed();
			}
		};

		Method sayHello = method(TestService.class, "sayHello");
		TestService proxy = ProxyFactory.createProxy(target,
				Map.of(sayHello, spec(interceptor, TestIntercepted.class)));
		// greet() is not in the plan, so it is not intercepted.
		String result = proxy.greet("Alice");
		assertEquals("Hello, Alice!", result);
	}

	@Test
	void shouldHandleMultipleInterceptors() {
		TestService target = new TestServiceImpl();
		MethodInterceptor interceptor1 = new FirstTestInterceptor();
		MethodInterceptor interceptor2 = new SecondTestInterceptor();

		Method sayHello = method(TestService.class, "sayHello");
		TestService proxy = ProxyFactory.createProxy(target,
				Map.of(sayHello, spec(List.of(interceptor1, interceptor2), TestIntercepted.class)));
		String result = proxy.sayHello();
		// Interceptors are executed in order: interceptor1 wraps interceptor2
		assertEquals("First: Second: Hello", result);
	}

	@Test
	void shouldHonorInterfaceOnlyBindingPlan() {
		// The "explicit interface AOP" style: the binding lives only on the
		// interface methods, expressed here as the proxy plan. Both methods are
		// wrapped; the factory honours whatever the plan declares.
		InterfaceAnnotatedService target = new InterfaceAnnotatedServiceImpl();
		MethodInterceptor interceptor = new TestInterceptor();

		Method sayHello = method(InterfaceAnnotatedService.class, "sayHello");
		Method greet = method(InterfaceAnnotatedService.class, "greet", String.class);
		InterfaceAnnotatedService proxy = ProxyFactory.createProxy(target, Map.of(sayHello,
				spec(interceptor, TestIntercepted.class), greet, spec(interceptor, TestIntercepted.class)));
		assertEquals("Intercepted: Hello", proxy.sayHello());
		assertEquals("Intercepted: Hi Bob", proxy.greet("Bob"));
	}

	public interface InterfaceAnnotatedService {
		@TestIntercepted
		String sayHello();

		@TestIntercepted
		String greet(String name);
	}

	public static class InterfaceAnnotatedServiceImpl implements InterfaceAnnotatedService {
		@Override
		public String sayHello() {
			return "Hello";
		}

		@Override
		public String greet(String name) {
			return "Hi " + name;
		}
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
