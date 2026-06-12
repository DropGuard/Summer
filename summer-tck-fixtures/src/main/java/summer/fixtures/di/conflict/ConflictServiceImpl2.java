package summer.fixtures.di.conflict;

public class ConflictServiceImpl2 implements ConflictService {
	@Override
	public void doSomething() {
		// Intentionally empty --test fixture for ambiguous dependency detection
	}
}
