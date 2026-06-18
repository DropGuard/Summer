package summer.tck.di;

import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.di.conflict.ConflictConfig;

public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerFailure() {
		RuntimeApplicationContext.builder().registerComponent(ConflictConfig.class).build();
	}
}
