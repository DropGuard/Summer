package summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public abstract class AbstractCircularDependencyTCK {

	protected abstract void triggerCircularDependency();

	@Test
	void testCircularDependencyFailsFast() {
		Exception exception = assertThrows(Exception.class, () -> {
			triggerCircularDependency();
		});

		assertTrue(exception.getMessage().contains("Circular") || exception.getMessage().contains("Compilation failed"),
				"Should fail fast with circular dependency error: " + exception.getMessage());
	}
}
