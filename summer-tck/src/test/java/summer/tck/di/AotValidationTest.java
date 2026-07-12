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

	private static BeanContainer aotContext() {
		try {

			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}