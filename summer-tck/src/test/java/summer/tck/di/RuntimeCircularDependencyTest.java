package summer.tck.di;

import summer.fixtures.di.circular.CircularA;
import summer.fixtures.di.circular.CircularB;
import summer.test.TestContainerBuilder;
import summer.test.annotation.WithFixtures;

/**
 * Runtime test for circular dependency detection.
 */
@WithFixtures({CircularA.class, CircularB.class})
public class RuntimeCircularDependencyTest extends AbstractCircularDependencyTCK {

	@Override
	protected void triggerFailure() {
		TestContainerBuilder.create().withEntryBeans(CircularA.class, CircularB.class).build();
	}
}
