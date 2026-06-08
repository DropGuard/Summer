package summer.fixtures.aop;

import summer.core.Component;

/**
 * Concrete implementation. greet() is annotated with @Logged for interception;
 * shout() is not. This lets us verify that ONLY annotated methods are proxied.
 */
@Component
public class GreeterService implements Greeter {

	@Logged
	@Override
	public String greet(String name) {
		return "Hello, " + name;
	}

	// NOT annotated with @Logged --interception must NOT apply here
	@Override
	public String shout(String message) {
		return message.toUpperCase();
	}
}
