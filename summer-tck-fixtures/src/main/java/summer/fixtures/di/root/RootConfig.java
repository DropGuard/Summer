package summer.fixtures.di.root;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Entry point for the empty-prefix ConfigProperties test.
 */
@Configuration
public class RootConfig {

	@Bean
	public RootService rootService(RootProperties properties) {
		return new RootService(properties);
	}
}
