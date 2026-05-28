package summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public abstract class AbstractMultiModuleConflictTCK {

	protected abstract void triggerConflict();

	@Test
	void testAmbiguousDependencyFailsFast() {
		Exception exception = assertThrows(Exception.class, () -> {
			triggerConflict();
		});

		assertTrue(
				exception.getMessage().contains("Ambiguous dependency")
						|| exception.getMessage().contains("Multiple beans found")
						|| exception.getMessage().contains("Compilation failed"),
				"Should fail fast with ambiguous dependency error: " + exception.getMessage());
	}
}
