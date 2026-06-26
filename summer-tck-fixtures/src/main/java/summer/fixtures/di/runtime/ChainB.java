package summer.fixtures.di.runtime;

import summer.core.Component;

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
