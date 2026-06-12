package summer.tck.di;

import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.circular.CircularA;
import summer.fixtures.di.circular.CircularB;

public class RuntimeCircularDependencyTest extends AbstractCircularDependencyTCK {

	@Override
	protected void triggerFailure() {
		var ctx = new RuntimeApplicationContext();
		ctx.registerComponent(CircularA.class);
		ctx.registerComponent(CircularB.class);
		ctx.initializeBeans();
	}
}
