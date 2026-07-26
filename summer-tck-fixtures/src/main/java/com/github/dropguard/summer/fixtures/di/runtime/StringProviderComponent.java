package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.Provider;

@Component
public class StringProviderComponent implements Provider<String> {
	@Override
	public String provide() {
		return "Hello Provider";
	}
}
