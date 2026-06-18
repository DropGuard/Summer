package summer.tck.di;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import summer.runtime.RuntimeApplicationContext;
import summer.core.Engine;

/**
 * Runtime (reflection-based) conditional assembly test.
 */
public class RuntimeConditionalTCKTest extends AbstractConditionalTCK {

	@BeforeEach
	void setUp() {
		context = RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
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
