package com.github.dropguard.summer.fixtures.di.replaces;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;

@Configuration
@Replaces(OriginalBeanConfig.class)
public class ReplacementBeanConfig {

	@Bean
	public ServiceBean serviceBean() {
		return new ServiceBean("replacement");
	}
}
