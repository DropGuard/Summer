package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine test for {@code @ConfigurationProperties} auto-binding.
 */
public class AotConfigurationPropertiesTest extends AbstractConfigurationPropertiesTCK {

	@Override
	protected BeanContainer createContext() {
		return aotContext();
	}

	private static BeanContainer aotContext() {
		try {
			
			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}