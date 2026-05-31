package summer.tck.aop.dummy;

import summer.aop.Intercepted;
import summer.core.Component;

/**
 * Concrete implementation. greet() is marked for interception; shout() is not.
 * This lets us verify that ONLY annotated methods are proxied.
 */
@Component
public class GreeterService implements Greeter {

	@Intercepted
	@Override
	public String greet(String name) {
		return "Hello, " + name;
	}

	// NOT annotated with @Intercepted — interception must NOT apply here
	@Override
	public String shout(String message) {
		return message.toUpperCase();
	}
}
