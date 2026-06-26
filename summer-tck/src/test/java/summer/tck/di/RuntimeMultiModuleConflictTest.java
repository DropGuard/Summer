package summer.tck.di;

import summer.fixtures.di.conflict.ConflictConfig;
import summer.test.TestContainerBuilder;

/**
 * Runtime test for multi-module bean conflict detection.
 */
public class RuntimeMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerFailure() {
		TestContainerBuilder.create().withEntryBeans(ConflictConfig.class).build();
	}
}
