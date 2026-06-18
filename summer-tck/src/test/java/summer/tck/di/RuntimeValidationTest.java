package summer.tck.di;

import summer.core.BeanContainer;
import summer.fixtures.validation.ValidationConfig;
import summer.runtime.RuntimeApplicationContext;
import summer.core.Engine;

/**
 * Runtime engine validation tests.
 */
public class RuntimeValidationTest extends AbstractValidationTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}

	@Override
	protected BeanContainer createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.builder().registerComponent(entryPoint).build();
	}
}