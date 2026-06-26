package summer.fixtures.di.runtime;

import summer.core.Component;

@Component
public class GreetingServiceImpl implements GreetingService {
	@Override
	public String greet(String name) {
		return "Hello, " + name;
	}
}
