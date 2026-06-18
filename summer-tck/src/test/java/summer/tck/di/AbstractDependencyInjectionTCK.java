package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.tck.AbstractContextTCK;
import summer.fixtures.dummy.ServiceA;
import summer.fixtures.dummy.ServiceB;
import summer.fixtures.dummy.ServiceC;

/**
 * TCK for core dependency injection behavior.
 *
 * <p>
 * Verifies:
 * <ul>
 * <li>Context creation succeeds</li>
 * <li>Singleton uniqueness</li>
 * <li>Constructor injection resolution</li>
 * </ul>
 */
public abstract class AbstractDependencyInjectionTCK extends AbstractContextTCK {

	@Test
	void testContextStartsSuccessfully() {
		assertNotNull(context(), "BeanContainer should not be null");
	}

	@Test
	void testSingletonUniqueness() {
		BeanContainer ctx = context();
		ServiceC c1 = ctx.getBean(ServiceC.class);
		ServiceC c2 = ctx.getBean(ServiceC.class);
		assertNotNull(c1);
		assertSame(c1, c2, "Multiple calls to getBean should return the same singleton instance");
	}

	@Test
	void testDependencyResolution() {
		BeanContainer ctx = context();

		ServiceA a = ctx.getBean(ServiceA.class);
		ServiceB b = ctx.getBean(ServiceB.class);
		ServiceC c = ctx.getBean(ServiceC.class);

		assertNotNull(a);
		assertNotNull(b);
		assertNotNull(c);

		assertSame(b, a.getServiceB(), "ServiceA should be injected with the singleton ServiceB");
		assertSame(c, b.getServiceC(), "ServiceB should be injected with the singleton ServiceC");
	}
}
