package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.dummy.ServiceA;

public class RuntimeCrossModuleDiscoveryTest extends AbstractCrossModuleDiscoveryTCK {

	@Override
	protected ApplicationContext createContext() {
		// Scan summer.tck.dummy — the ComponentScanner will also load
		// META-INF/jandex.idx from summer-tck-fixtures JAR on the classpath,
		// discovering summer.fixtures.dummy.ServiceA/B/C automatically.
		return RuntimeApplicationContext.create(ServiceA.class);
	}
}


