package summer.tck.di.replaces;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * A @Configuration that produces a ServiceBean. To be replaced by
 * ReplacementBeanConfig — verifying that @Bean methods are cascade-removed.
 */
@Configuration
public class OriginalBeanConfig {

	@Bean
	public ServiceBean serviceBean() {
		return new ServiceBean("original");
	}
}
