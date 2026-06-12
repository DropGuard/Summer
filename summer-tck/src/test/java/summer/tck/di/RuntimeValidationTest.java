package summer.tck.di;

import summer.core.ApplicationContext;
import summer.fixtures.validation.ValidationConfig;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime engine validation tests.
 */
public class RuntimeValidationTest extends AbstractValidationTCK {

	@Override
	protected ApplicationContext createContext() {
		var ctx = new RuntimeApplicationContext();
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		var ctx = new RuntimeApplicationContext();
		ctx.registerComponent(entryPoint);
		ctx.scan();
		ctx.initializeBeans();
		return ctx;
	}
}
