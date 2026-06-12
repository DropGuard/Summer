package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.dummy.ServiceA;

public class RuntimeDiTest extends AbstractDependencyInjectionTCK {

	@Override
	protected ApplicationContext createContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}
}
