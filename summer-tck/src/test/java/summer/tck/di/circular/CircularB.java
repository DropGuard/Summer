package summer.tck.di.circular;

import summer.core.Component;

@Component
public class CircularB {
	private final CircularA a;

	public CircularB(CircularA a) {
		this.a = a;
	}
}
