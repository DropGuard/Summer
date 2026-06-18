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
		return RuntimeApplicationContext.create();
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.builder().registerComponent(entryPoint).build();
	}
}