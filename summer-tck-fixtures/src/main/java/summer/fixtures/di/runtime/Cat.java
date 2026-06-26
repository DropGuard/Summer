package summer.fixtures.di.runtime;

import summer.core.Component;

@Component
public class Cat implements Animal {
	@Override
	public String sound() {
		return "meow";
	}
}
