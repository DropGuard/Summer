package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class ChainA {
	private final ChainB b;

	public ChainA(ChainB b) {
		this.b = b;
	}

	public ChainB getB() {
		return b;
	}
}
