package summer.fixtures.di.missing;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Entry point for testing missing required configuration fields.
 */
@Configuration
public class StrictConfig {

	@Bean
	public StrictService strictService(StrictProperties properties) {
		return new StrictService(properties);
	}
}
