package com.github.dropguard.summer.fixtures.di.generic;

import com.github.dropguard.summer.core.Component;

@Component
public class StringServiceImpl implements GenericService<String> {

	@Override
	public String process(String input) {
		return "processed:" + input;
	}
}
