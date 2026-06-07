package summer.tck.di;

import org.junit.jupiter.api.Test;
import summer.tck.AbstractFailureTCK;

/**
 * TCK for ambiguous dependency detection.
 *
 * <p>
 * Verifies that the DI container fails fast when multiple beans implement the
 * same interface.
 * </p>
 */
public abstract class AbstractMultiModuleConflictTCK extends AbstractFailureTCK {

	@Test
	void testAmbiguousDependencyFailsFast() {
		assertFailureContains("Ambiguous dependency", "Multiple beans found", "Compilation failed");
	}
}
