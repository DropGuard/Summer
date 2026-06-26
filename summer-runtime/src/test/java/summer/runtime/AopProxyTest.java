package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.aop.MethodInterceptor;
import summer.aop.SummerAopException;
import summer.core.BeanContainer;
import summer.fixtures.di.runtime.InterceptedService;
import summer.fixtures.di.runtime.InterceptedServiceImpl;
import summer.fixtures.di.runtime.TestInterceptor1;
import summer.fixtures.di.runtime.TestInterceptor2;
import summer.fixtures.di.runtime.TestInterceptorComponent;

class AopProxyTest {

	private static BeanContainer ctx;

	@BeforeAll
	static void setUp() {
		ctx = RuntimeBeanContainerBuilder.buildFromSeeds(InterceptedServiceImpl.class, TestInterceptorComponent.class);
	}

	@Test
	void interfaceBeanIsProxy() {
		InterceptedService greeting = ctx.getBean(InterceptedService.class);
		assertNotEquals(InterceptedServiceImpl.class, greeting.getClass(),
				"Interface lookup must return proxy, not raw impl");
		assertTrue(greeting instanceof InterceptedService);
	}

	@Test
	void concreteClassBypassesAop() {
		InterceptedServiceImpl raw = ctx.getBean(InterceptedServiceImpl.class);
		assertEquals(InterceptedServiceImpl.class, raw.getClass(),
				"Concrete class lookup must return raw instance, not proxy");
	}

	@Test
	void proxyFactoryWithInterceptedMethod() {
		InterceptedServiceImpl target = new InterceptedServiceImpl();
		InterceptedService proxy = ProxyFactory.createProxy(target, List.of(new TestInterceptorComponent()));
		String result = proxy.interceptedGreet("Test");
		assertEquals("[proxied] Hello, Test", result);
	}

	@Test
	void proxyFactoryPassesThroughNonIntercepted() {
		InterceptedServiceImpl target = new InterceptedServiceImpl();
		MethodInterceptor interceptor = new MethodInterceptor() {
			@Override
			public Object intercept(summer.aop.InterceptorChain chain) throws Throwable {
				return "[proxied] " + chain.proceed();
			}
		};
		InterceptedService proxy = ProxyFactory.createProxy(target, List.of(interceptor));
		String result = proxy.nonInterceptedGreet("Test");
		assertEquals("Hello, Test", result, "Non-intercepted method must not be wrapped");
	}

	@Test
	void proxyFactoryRejectsNonInterface() {
		Object target = new Object();
		assertThrows(SummerAopException.class, () -> ProxyFactory.createProxy(target, List.of()));
	}

	@Test
	void multipleInterceptorsInOrder() {
		InterceptedServiceImpl target = new InterceptedServiceImpl();
		MethodInterceptor first = new TestInterceptor1();
		MethodInterceptor second = new TestInterceptor2();
		InterceptedService proxy = ProxyFactory.createProxy(target, List.of(first, second));
		String result = proxy.interceptedGreet("X");
		assertEquals("[1][2]Hello, X", result, "Interceptors must chain in order");
	}
}
