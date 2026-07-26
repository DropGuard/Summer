package com.github.dropguard.summer.fixtures.di.missing;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

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
