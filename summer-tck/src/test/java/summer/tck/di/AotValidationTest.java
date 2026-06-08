package summer.tck.di;

import summer.core.ApplicationContext;
import summer.core.aot.GeneratedAotContext;

/**
 * AOT engine validation tests.
 */
public class AotValidationTest extends AbstractValidationTCK {

	@Override
	protected ApplicationContext createContext() {
		return new GeneratedAotContext();
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return new GeneratedAotContext();
	}
}
