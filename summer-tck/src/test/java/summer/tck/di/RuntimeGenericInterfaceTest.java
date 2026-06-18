package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.generic.StringServiceImpl;

/**
 * Runtime test for generic interface dependency resolution.
 */
public class RuntimeGenericInterfaceTest extends AbstractGenericInterfaceTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create();
	}
}
