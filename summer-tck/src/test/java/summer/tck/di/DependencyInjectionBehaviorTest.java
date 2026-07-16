package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.fixtures.dummy.ServiceA;
import summer.fixtures.dummy.ServiceB;
import summer.fixtures.dummy.ServiceC;
import summer.test.annotation.SummerTest;

/**
 * Verifies core DI behaviour via {@link SummerTest}. Each {@code @Test} uses
 * the Runtime engine (dev mode default).
 */

@SummerTest
public class DependencyInjectionBehaviorTest {

	private final BeanContainer context;

	public DependencyInjectionBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context, "BeanContainer should not be null");
	}

	@Test
	void testSingletonUniqueness() {
		ServiceC c1 = context.getBean(ServiceC.class);
		ServiceC c2 = context.getBean(ServiceC.class);
		assertNotNull(c1);
		assertSame(c1, c2, "Multiple calls to getBean should return the same singleton instance");
	}

	@Test
	void testDependencyResolution() {
		ServiceA a = context.getBean(ServiceA.class);
		ServiceB b = context.getBean(ServiceB.class);
		ServiceC c = context.getBean(ServiceC.class);

		assertNotNull(a);
		assertNotNull(b);
		assertNotNull(c);

		assertSame(b, a.getServiceB(), "ServiceA should be injected with the singleton ServiceB");
		assertSame(c, b.getServiceC(), "ServiceB should be injected with the singleton ServiceC");
	}
}
