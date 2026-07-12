package summer.tck.di;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * AOT engine conditional assembly test.
 *
 * <p>
 * Runs the same TCK as {@link RuntimeConditionalTCKTest} but against the
 * AOT-generated context. Both engines must produce identical conditional
 * assembly behavior.
 * </p>
 */
public class AotConditionalTCKTest extends AbstractConditionalTCK {

	@BeforeEach
	void setUp() {
		context = summer.test.TestContainerBuilder.buildAot(null);
	}

	@AfterEach
	void tearDown() {
		closeQuietly(context);
	}

	@Override
	protected void cleanupComponent() {
		// Handled by tearDown
	}
}
