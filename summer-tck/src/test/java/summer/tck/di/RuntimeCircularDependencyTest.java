package summer.tck.di;

import summer.scanner.runtime.RuntimeDiEngine;
import summer.tck.di.circular.CircularA;

public class RuntimeCircularDependencyTest extends AbstractCircularDependencyTCK {

	@Override
	protected void triggerCircularDependency() {
		new RuntimeDiEngine().create(CircularA.class);
	}
}
