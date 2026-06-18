package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.inheritance.ServiceImpl;
import summer.core.Engine;

/**
 * Runtime test for interface inheritance dependency resolution.
 */
public class RuntimeInterfaceInheritanceTest extends AbstractInterfaceInheritanceTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}
}
