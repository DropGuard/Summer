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
			Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
			return (BeanContainer) aotClass.getMethod("build").invoke(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}