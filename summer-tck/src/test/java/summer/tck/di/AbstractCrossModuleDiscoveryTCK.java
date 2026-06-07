package summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.tck.AbstractContextTCK;

/**
 * TCK for cross-module bean discovery.
 *
 * <p>
 * Verifies that beans defined in a separate JAR ({@code summer-tck-fixtures})
 * are discovered via the pre-built Jandex index and can be injected into the
 * application context alongside beans from the current module.
 * </p>
 */
public abstract class AbstractCrossModuleDiscoveryTCK extends AbstractContextTCK {

	@Test
	void discoversBeanFromExternalModule() {
		// ServiceA is defined in summer-tck-fixtures (separate JAR),
		// discovered via META-INF/jandex.idx on the classpath.
		assertDoesNotThrow(() -> context().getBean(summer.fixtures.dummy.ServiceA.class),
				"Bean from external module (summer-tck-fixtures) should be discoverable");
	}

	@Test
	void resolvesCrossModuleDependencyChain() {
		summer.fixtures.dummy.ServiceA a = context().getBean(summer.fixtures.dummy.ServiceA.class);
		assertNotNull(a.getServiceB(), "ServiceA should have ServiceB injected");
		assertNotNull(a.getServiceB().getServiceC(), "ServiceB should have ServiceC injected");
		assertEquals("Hello from C", a.getServiceB().getServiceC().getMessage());
	}

	@Test
	void crossModuleBeansAreSingletons() {
		summer.fixtures.dummy.ServiceA a1 = context().getBean(summer.fixtures.dummy.ServiceA.class);
		summer.fixtures.dummy.ServiceA a2 = context().getBean(summer.fixtures.dummy.ServiceA.class);
		assertSame(a1, a2, "Cross-module beans should be singletons");
	}
}
