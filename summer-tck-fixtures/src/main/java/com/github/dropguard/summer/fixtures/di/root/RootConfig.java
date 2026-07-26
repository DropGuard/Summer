package com.github.dropguard.summer.fixtures.di.root;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;

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
