package summer.tck.di.circular;

import summer.core.Component;

@Component
public class CircularA {
	private final CircularB b;

	public CircularA(CircularB b) {
		this.b = b;
	}
}
