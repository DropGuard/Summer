package summer.compiler.dummy;

import summer.core.Component;

@Component
public class DummyService implements DummyInterface {
	@DummyAnnotation
	public String hello(String name) {
		return "Hello " + name;
	}
}
