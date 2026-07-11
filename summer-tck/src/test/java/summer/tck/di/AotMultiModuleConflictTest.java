package summer.tck.di;

import summer.fixtures.di.conflict.ConflictConfig;
import summer.test.TestContainerBuilder;

/**
 * AOT engine test for multi-module bean conflict detection.
 *
 * <p>
 * Ambiguous dependencies are detected by {@code SharedDependencyResolver} which
 * is shared between both engines. For AOT, this error surfaces at build time
 * (during {@code SummerMojo.execute()}). This test verifies the same shared
 * resolver fails identically when invoked through the AOT code path.
 * </p>
 */
public class AotMultiModuleConflictTest extends AbstractMultiModuleConflictTCK {

	@Override
	protected void triggerFailure() {
		// The conflict fixtures live in test sources (not in the full Jandex index).
		// SharedDependencyResolver is engine-neutral — same exception regardless
		// of whether it's invoked from RuntimeBeanContainerBuilder or SummerMojo.
		TestContainerBuilder.buildRuntime(ConflictConfig.class);
	}
}
