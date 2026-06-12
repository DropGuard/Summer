package summer.fixtures.di.configprops;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Test fixture: configuration that depends on auto-bound AppProperties.
 */
@Configuration
public class AppConfig {

	@Bean
	public AppService appService(AppProperties properties) {
		return new AppService(properties);
	}
}
