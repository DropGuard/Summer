package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

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
