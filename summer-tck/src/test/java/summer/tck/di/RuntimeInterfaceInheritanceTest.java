package summer.tck.di;

import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.di.inheritance.ServiceImpl;

/**
 * Runtime test for interface inheritance dependency resolution.
 */
public class RuntimeInterfaceInheritanceTest extends AbstractInterfaceInheritanceTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(ServiceImpl.class);
	}
}
