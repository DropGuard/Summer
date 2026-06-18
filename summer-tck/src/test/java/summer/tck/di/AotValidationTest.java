package summer.tck.di;

import summer.core.BeanContainer;

/**
 * AOT engine validation tests.
 */
public class AotValidationTest extends AbstractValidationTCK {

	@Override
	protected BeanContainer createContext() {
		return aotContext();
	}

	@Override
	protected BeanContainer createContext(Class<?> entryPoint) {
		return aotContext();
	}

	private static BeanContainer aotContext() {
		try {
			Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
			return (BeanContainer) aotClass.getMethod("create").invoke(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}