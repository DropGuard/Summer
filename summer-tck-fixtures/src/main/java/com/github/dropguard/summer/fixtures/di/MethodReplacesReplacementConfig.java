package com.github.dropguard.summer.fixtures.di;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.annotation.Replaces;

/**
 * Replacement configuration that uses method-level @Replaces to replace the
 * bean by return type.
 */
@Configuration
public class MethodReplacesReplacementConfig {

	@Bean
	@Replaces(MethodReplacesBean.class)
	public MethodReplacesBean methodReplacesBean() {
		return new MethodReplacesBean("replaced");
	}
}
