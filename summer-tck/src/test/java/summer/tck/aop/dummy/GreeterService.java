package summer.tck.aop.dummy;

import summer.core.Component;

/**
 * Concrete implementation. greet() is annotated with @Recorded for
 * interception; shout() is not. This lets us verify that ONLY annotated methods
 * are proxied.
 */
@Component
public class GreeterService implements Greeter {

	@Recorded
	@Override
	public String greet(String name) {
		return "Hello, " + name;
	}

	// NOT annotated with @Recorded — interception must NOT apply here
	@Override
	public String shout(String message) {
		return message.toUpperCase();
	}
}
