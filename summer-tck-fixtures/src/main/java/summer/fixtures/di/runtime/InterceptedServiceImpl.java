package summer.fixtures.di.runtime;

import summer.core.Component;

@Component
public class InterceptedServiceImpl implements InterceptedService {
	@Override
	@TestIntercepted
	public String interceptedGreet(String name) {
		return "Hello, " + name;
	}

	@Override
	public String nonInterceptedGreet(String name) {
		return "Hello, " + name;
	}
}
