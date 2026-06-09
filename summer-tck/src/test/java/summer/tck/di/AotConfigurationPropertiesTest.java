package summer.tck.di;

import summer.core.ApplicationContext;
import summer.core.aot.GeneratedAotContext;

/**
 * AOT engine test for {@code @ConfigurationProperties} auto-binding.
 *
 * <p>
 * Verifies that the code-generated {@link GeneratedAotContext} produces the
 * same config-binding behaviour as the runtime engine (see
 * {@link RuntimeConfigurationPropertiesTest}).
 * </p>
 */
public class AotConfigurationPropertiesTest extends AbstractConfigurationPropertiesTCK {

	@Override
	protected ApplicationContext createContext() {
		return new GeneratedAotContext();
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return new GeneratedAotContext();
	}
}
