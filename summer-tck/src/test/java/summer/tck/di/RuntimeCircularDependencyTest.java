package summer.tck.di;

import summer.runtime.RuntimeApplicationContext;
import summer.tck.di.circular.CircularA;

public class RuntimeCircularDependencyTest extends AbstractCircularDependencyTCK {

	@Override
	protected void triggerFailure() {
		RuntimeApplicationContext.create(CircularA.class);
	}
}


