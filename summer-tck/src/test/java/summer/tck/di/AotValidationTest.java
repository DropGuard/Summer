package summer.tck.di;

import summer.core.ApplicationContext;

/**
 * AOT engine validation tests.
 */
public class AotValidationTest extends AbstractValidationTCK {

	@Override
	protected ApplicationContext createContext() {
		return aotContext();
	}

	@Override
	protected ApplicationContext createContext(Class<?> entryPoint) {
		return aotContext();
	}

	private static ApplicationContext aotContext() {
		try {
			Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
			return (ApplicationContext) aotClass.getMethod("create").invoke(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}