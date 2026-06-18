package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.dummy.ServiceA;

public class RuntimeDiTest extends AbstractDependencyInjectionTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create();
	}
}
