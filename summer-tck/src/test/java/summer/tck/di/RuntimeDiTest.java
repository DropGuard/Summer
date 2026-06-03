package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeDiEngine;
import summer.tck.dummy.ServiceA;

public class RuntimeDiTest extends AbstractDependencyInjectionTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(ServiceA.class);
	}
}
