package summer.tck;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

/**
 * Base class for TCK tests that verify failure scenarios.
 *
 * <p>
 * Use for:
 * <ul>
 * <li>Circular dependency detection</li>
 * <li>Ambiguous dependency detection</li>
 * <li>Other scenarios that should fail fast</li>
 * </ul>
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * public abstract class AbstractCircularTCK extends AbstractFailureTCK {
 * 	// Inherit assertFailureContains() helper
 * }
 *
 * public class RuntimeCircularTest extends AbstractCircularTCK {
 * 	&#64;Override
 * 	protected void triggerFailure() {
 * 		RuntimeApplicationContext.create(CircularA.class);
 * 	}
 *
 * 	&#64;Test
 * 	void testCircularFails() {
 * 		assertFailureContains("Circular", "cycle");
 * 	}
 * }
 * </pre>
 */
public abstract class AbstractFailureTCK extends AbstractTCK {

	/**
	 * Trigger the operation that should fail.
	 *
	 * <p>
	 * Implementations should call the code that is expected to throw an exception.
	 */
	protected abstract void triggerFailure();

	/**
	 * Assert that {@link #triggerFailure()} throws an exception whose message
	 * contains one of the expected strings.
	 */
	protected void assertFailureContains(String... expectedMessages) {
		Exception exception = assertThrows(Exception.class, this::triggerFailure);

		boolean matches = Arrays.stream(expectedMessages).anyMatch(msg -> exception.getMessage().contains(msg));

		assertTrue(matches, "Expected failure message to contain one of " + Arrays.toString(expectedMessages)
				+ " but was: " + exception.getMessage());
	}
}
