package summer.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.core.Provider;
import summer.core.exception.NoSuchBeanException;
import summer.fixtures.di.runtime.Animal;
import summer.fixtures.di.runtime.Cat;
import summer.fixtures.di.runtime.ChainA;
import summer.fixtures.di.runtime.ConsumerService;
import summer.fixtures.di.runtime.Dog;
import summer.fixtures.di.runtime.GreetingService;
import summer.fixtures.di.runtime.GreetingServiceImpl;
import summer.fixtures.di.runtime.ProducedBean;
import summer.fixtures.di.runtime.ProducerConfig;
import summer.fixtures.di.runtime.SimpleService;
import summer.fixtures.di.runtime.StandaloneBean;
import summer.fixtures.di.runtime.StringProviderComponent;
import summer.fixtures.di.runtime.UnregisteredService;
import summer.web.Request;

class DiEngineTest {

	private static BeanContainer ctx;

	@BeforeAll
	static void setUp() {
		ctx = RuntimeBeanContainerBuilder.buildFromSeeds(ConsumerService.class, GreetingServiceImpl.class,
				StandaloneBean.class, ProducerConfig.class, StringProviderComponent.class, Dog.class, Cat.class,
				ChainA.class);
	}

	@AfterAll
	static void tearDown() throws Exception {
		ctx.close();
	}

	// ---- P0: DI core ----

	@Test
	void singletonReturnsSameInstance() {
		SimpleService s1 = ctx.getBean(SimpleService.class);
		SimpleService s2 = ctx.getBean(SimpleService.class);
		assertSame(s1, s2, "Singleton must return identical instance");
	}

	@Test
	void constructorInjectionResolves() {
		ConsumerService consumer = ctx.getBean(ConsumerService.class);
		assertNotNull(consumer);
		assertNotNull(consumer.getDependency(), "Constructor-injected dependency must not be null");
		assertSame(SimpleService.class, consumer.getDependency().getClass());
	}

	@Test
	void interfaceResolvesToImpl() {
		GreetingService greeting = ctx.getBean(GreetingService.class);
		assertNotNull(greeting);
		assertEquals("Hello, World", greeting.greet("World"));
	}

	@Test
	void missingBeanThrows() {
		assertThrows(NoSuchBeanException.class, () -> ctx.getBean(UnregisteredService.class));
	}

	@Test
	void configurationBeanProducer() {
		ProducedBean bean = ctx.getBean(ProducedBean.class);
		assertNotNull(bean);
		assertEquals("produced-value", bean.getValue());
	}

	@Test
	void providerPattern() {
		String provided = ctx.getBean(String.class);
		assertEquals("Hello Provider", provided);
	}

	@Test
	void getBeansOfType() {
		List<Animal> animals = ctx.getBeans(Animal.class);
		assertFalse(animals.isEmpty(), "getBeansOfType must find implementations");
		assertTrue(animals.size() >= 2, "Must find both Dog and Cat");
	}

	@Test
	void multipleImplementationsDetected() {
		List<Animal> animals = ctx.getBeans(Animal.class);
		assertTrue(animals.size() >= 2, "Must find both Dog and Cat implementations");
		boolean hasDog = animals.stream().anyMatch(a -> a instanceof Dog);
		boolean hasCat = animals.stream().anyMatch(a -> a instanceof Cat);
		assertTrue(hasDog, "Must include Dog");
		assertTrue(hasCat, "Must include Cat");
	}

	// ---- P2: edge cases that need DI ----

	@Test
	void deepDependencyChain() {
		ChainA a = ctx.getBean(ChainA.class);
		assertNotNull(a);
		assertNotNull(a.getB());
		assertNotNull(a.getB().getC());
		assertNotNull(a.getB().getC().getD());
	}

	@Test
	void threadSafeSingletonAccess() throws Exception {
		int threadCount = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CyclicBarrier barrier = new CyclicBarrier(threadCount);
		ConcurrentHashMap<Integer, SimpleService> results = new ConcurrentHashMap<>();

		for (int i = 0; i < threadCount; i++) {
			final int idx = i;
			pool.submit(() -> {
				try {
					barrier.await(5, TimeUnit.SECONDS);
					results.put(idx, ctx.getBean(SimpleService.class));
				} catch (Exception e) {
					fail("Thread failed: " + e.getMessage());
				}
			});
		}

		pool.shutdown();
		assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

		SimpleService first = results.get(0);
		for (int i = 1; i < threadCount; i++) {
			assertSame(first, results.get(i), "Thread " + i + " got a different singleton instance");
		}
	}

	@Test
	void beanWithNoDependencies() {
		StandaloneBean bean = ctx.getBean(StandaloneBean.class);
		assertNotNull(bean);
		assertEquals("standalone", bean.getValue());
	}

	@Test
	void contextCloseIsIdempotent() {
		BeanContainer isolated = RuntimeBeanContainerBuilder.buildFromSeeds(SimpleService.class);
		assertDoesNotThrow(isolated::close);
		assertDoesNotThrow(isolated::close, "Double close must not throw");
	}

	// ---- P2: edge cases without DI ----

	@Test
	void providerGivesFreshInstance() {
		Provider<SimpleService> provider = SimpleService::new;
		SimpleService s1 = provider.provide();
		SimpleService s2 = provider.provide();
		assertNotSame(s1, s2, "Provider must create new instance each time");
	}

	@Test
	void requestNullBodyDefaultsToEmpty() {
		Request req = new Request(summer.web.HttpMethod.POST, "/test", null, "application/json", null);
		assertNotNull(req.getBody());
		assertEquals(0, req.getBody().length);
	}
}
