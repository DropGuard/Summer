package summer.tck.di.replaces;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;

/**
 * Replaces {@link OriginalBeanConfig} and its @Bean products.
 */
@Configuration
@Replaces(OriginalBeanConfig.class)
public class ReplacementBeanConfig {

	@Bean
	public ServiceBean serviceBean() {
		return new ServiceBean("replacement");
	}
}
