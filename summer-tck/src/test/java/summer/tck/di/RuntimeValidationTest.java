package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.validation.ValidationConfig;

/**
 * Runtime engine validation tests.
 */
public class RuntimeValidationTest extends AbstractValidationTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create(ValidationConfig.class);
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.create(entryPoint);
	}
}
