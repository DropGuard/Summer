package summer.tck.di;

import summer.core.ApplicationContext;
import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.di.generic.StringServiceImpl;

/**
 * Runtime test for generic interface dependency resolution.
 */
public class RuntimeGenericInterfaceTest extends AbstractGenericInterfaceTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(StringServiceImpl.class);
	}
}
