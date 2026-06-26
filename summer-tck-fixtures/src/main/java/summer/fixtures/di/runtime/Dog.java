package summer.fixtures.di.runtime;

import summer.core.Component;

@Component
public class Dog implements Animal {
	@Override
	public String sound() {
		return "woof";
	}
}
