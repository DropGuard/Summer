package summer.tck.di;

import org.junit.jupiter.api.Test;
import summer.tck.AbstractFailureTCK;

/**
 * TCK for circular dependency detection.
 *
 * <p>
 * Verifies that the DI container fails fast when circular dependencies are
 * detected.
 * </p>
 */
public abstract class AbstractCircularDependencyTCK extends AbstractFailureTCK {

	@Test
	void testCircularDependencyFailsFast() {
		assertFailureContains("Circular", "Compilation failed");
	}
}
