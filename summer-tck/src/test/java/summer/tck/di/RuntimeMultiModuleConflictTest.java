package summer.tck.di;

import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.conflict.ConflictConfig;

public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerFailure() {
		var ctx = new RuntimeApplicationContext();
		ctx.registerComponent(ConflictConfig.class);
		ctx.initializeBeans();
	}
}
