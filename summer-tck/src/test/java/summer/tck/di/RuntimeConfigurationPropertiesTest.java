package summer.tck.di;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.di.configprops.AppConfig;

/**
 * Runtime engine test for {@code @ConfigurationProperties} auto-binding.
 */
public class RuntimeConfigurationPropertiesTest extends AbstractConfigurationPropertiesTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create(AppConfig.class);
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.create(entryPoint);
	}
}
