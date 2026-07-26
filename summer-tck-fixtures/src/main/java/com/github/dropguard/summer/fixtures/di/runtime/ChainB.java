package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class ChainB {
	private final ChainC c;

	public ChainB(ChainC c) {
		this.c = c;
	}

	public ChainC getC() {
		return c;
	}
}
