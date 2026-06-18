package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.dummy.ServiceA;

public class RuntimeCrossModuleDiscoveryTest extends AbstractCrossModuleDiscoveryTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create();
	}
}
