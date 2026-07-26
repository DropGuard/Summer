package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class GreetingServiceImpl implements GreetingService {
	@Override
	public String greet(String name) {
		return "Hello, " + name;
	}
}
