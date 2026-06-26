package summer.fixtures.di.runtime;

import summer.core.Component;

@Component
public class SimpleService {
	public String doWork() {
		return "done";
	}
}
