package summer.tck.di;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.configprops.AppConfig;
import summer.core.Engine;

/**
 * Runtime engine test for {@code @ConfigurationProperties} auto-binding.
 */
public class RuntimeConfigurationPropertiesTest extends AbstractConfigurationPropertiesTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}

	@Override
	protected BeanContainer createContext(Class<?> entryPoint) {
		return RuntimeApplicationContext.builder().registerComponent(entryPoint).build();
	}
}