package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.dummy.ServiceA;

public class RuntimeCrossModuleDiscoveryTest extends AbstractCrossModuleDiscoveryTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create();
	}
}
