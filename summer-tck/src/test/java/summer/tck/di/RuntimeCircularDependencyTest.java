package summer.tck.di;

import summer.fixtures.di.circular.CircularA;
import summer.fixtures.di.circular.CircularB;
import summer.runtime.RuntimeBeanContainerBuilder;
import summer.test.annotation.WithFixtures;

/**
 * Runtime test for circular dependency detection.
 */
@WithFixtures({CircularA.class, CircularB.class})
public class RuntimeCircularDependencyTest extends AbstractCircularDependencyTCK {

	@Override
	protected void triggerFailure() {
		RuntimeBeanContainerBuilder.buildFromSeeds(CircularA.class, CircularB.class);
	}
}
