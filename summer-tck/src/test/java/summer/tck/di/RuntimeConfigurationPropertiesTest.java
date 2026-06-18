package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.configprops.AppConfig;

/**
 * Runtime engine test for {@code @ConfigurationProperties} auto-binding.
 */
public class RuntimeConfigurationPropertiesTest extends AbstractConfigurationPropertiesTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create();
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.builder().registerComponent(entryPoint).build();
	}
}