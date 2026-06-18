package summer.tck.di;

import summer.core.BeanContainer;
import summer.fixtures.validation.ValidationConfig;
import summer.runtime.RuntimeApplicationContext;

/**
 * Runtime engine validation tests.
 */
public class RuntimeValidationTest extends AbstractValidationTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.createRuntime();
	}

	@Override
	protected BeanContainer createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.builder().registerComponent(entryPoint).build();
	}
}