package summer.runtime;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Configuration for runtime infrastructure beans.
 *
 * <p>
 * Provides {@link ConfigurationLoader} which is used by the runtime DI engine
 * for YAML configuration loading.
 * </p>
 */
@Configuration
public class RuntimeInfrastructureConfiguration {

	@Bean
	public ConfigurationLoader configurationLoader() {
		return new ConfigurationLoader();
	}
}
