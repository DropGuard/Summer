package summer.tck.di.conflict;

import summer.core.Component;

@Component
public class ConflictServiceImpl1 implements ConflictService {
	@Override
	public void doSomething() {
		// Intentionally empty --test fixture for ambiguous dependency detection
	}
}
