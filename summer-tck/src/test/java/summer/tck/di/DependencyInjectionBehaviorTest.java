package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import summer.core.BeanContainer;
import summer.fixtures.dummy.ServiceA;
import summer.fixtures.dummy.ServiceB;
import summer.fixtures.dummy.ServiceC;
import summer.test.DualEngineExtension;
import summer.test.annotation.DualEngineTest;

/**
 * Verifies core DI behaviour on both engines via {@link DualEngineTest}.
 *
 * <p>
 * Single class replaces the old 3-file pattern
 * ({@code AbstractDependencyInjectionTCK} + {@code RuntimeDiTest} +
 * {@code AotDependencyInjectionTest}).
 * Each {@code @Test} method runs once per engine.
 * </p>
 */
@ExtendWith(DualEngineExtension.class)
@DualEngineTest(seeds = { ServiceA.class })
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
