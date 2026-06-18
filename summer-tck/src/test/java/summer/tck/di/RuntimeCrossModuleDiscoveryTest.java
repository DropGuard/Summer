package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.dummy.ServiceA;
import summer.core.Engine;

public class RuntimeCrossModuleDiscoveryTest extends AbstractCrossModuleDiscoveryTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}
}
