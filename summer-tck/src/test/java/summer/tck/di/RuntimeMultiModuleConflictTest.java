package summer.tck.di;

import summer.fixtures.di.conflict.ConflictConfig;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Runtime test for multi-module bean conflict detection.
 */
public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerFailure() {
		RuntimeBeanContainerBuilder.buildFromSeeds(ConflictConfig.class);
	}
}
