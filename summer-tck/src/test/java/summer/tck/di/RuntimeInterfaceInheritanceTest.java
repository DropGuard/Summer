package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.inheritance.ServiceImpl;

/**
 * Runtime test for interface inheritance dependency resolution.
 */
public class RuntimeInterfaceInheritanceTest extends AbstractInterfaceInheritanceTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create();
	}
}
