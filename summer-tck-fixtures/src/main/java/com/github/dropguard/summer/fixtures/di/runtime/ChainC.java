package com.github.dropguard.summer.fixtures.di.runtime;

import com.github.dropguard.summer.core.Component;

@Component
public class ChainC {
	private final ChainD d;

	public ChainC(ChainD d) {
		this.d = d;
	}

	public ChainD getD() {
		return d;
	}
}
