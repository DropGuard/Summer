package summer.tck.di;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import summer.runtime.RuntimeBeanContainerBuilder;

/**
 * Runtime (reflection-based) conditional assembly test.
 */
public class RuntimeConditionalTCKTest extends AbstractConditionalTCK {

	@BeforeEach
	void setUp() {
		context = RuntimeBeanContainerBuilder.build();
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
