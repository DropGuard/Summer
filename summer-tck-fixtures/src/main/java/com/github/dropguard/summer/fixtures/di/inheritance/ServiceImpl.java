package com.github.dropguard.summer.fixtures.di.inheritance;

import com.github.dropguard.summer.core.Component;

@Component
public class ServiceImpl implements ExtendedService {

	@Override
	public String serve() {
		return "base";
	}

	@Override
	public String extendedServe() {
		return "extended";
	}
}
