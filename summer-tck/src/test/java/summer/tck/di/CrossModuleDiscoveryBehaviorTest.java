package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.BeanContainer;
import summer.test.annotation.SummerTest;

@SummerTest
public class CrossModuleDiscoveryBehaviorTest {

	private final BeanContainer context;

	public CrossModuleDiscoveryBehaviorTest(BeanContainer context) {
		this.context = context;
	}

	@Test
	void discoversBeanFromExternalModule() {
		assertDoesNotThrow(() -> context.getBean(summer.fixtures.dummy.ServiceA.class),
				"Bean from external module (summer-tck-fixtures) should be discoverable");
	}

	@Test
	void resolvesCrossModuleDependencyChain() {
		summer.fixtures.dummy.ServiceA a = context.getBean(summer.fixtures.dummy.ServiceA.class);
		assertNotNull(a.getServiceB(), "ServiceA should have ServiceB injected");
		assertNotNull(a.getServiceB().getServiceC(), "ServiceB should have ServiceC injected");
		assertEquals("Hello from C", a.getServiceB().getServiceC().getMessage());
	}

	@Test
	void crossModuleBeansAreSingletons() {
		summer.fixtures.dummy.ServiceA a1 = context.getBean(summer.fixtures.dummy.ServiceA.class);
		summer.fixtures.dummy.ServiceA a2 = context.getBean(summer.fixtures.dummy.ServiceA.class);
		assertSame(a1, a2, "Cross-module beans should be singletons");
	}
}
