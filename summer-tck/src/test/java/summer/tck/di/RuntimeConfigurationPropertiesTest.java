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
