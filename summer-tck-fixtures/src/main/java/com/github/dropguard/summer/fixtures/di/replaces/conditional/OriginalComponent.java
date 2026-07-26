package com.github.dropguard.summer.fixtures.di.replaces.conditional;

import com.github.dropguard.summer.core.Component;

@Component
public class OriginalComponent implements ReplacableService {

	@Override
	public String serve() {
		return "original";
	}
}
