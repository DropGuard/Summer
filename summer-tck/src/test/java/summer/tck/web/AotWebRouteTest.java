package summer.tck.web;

import summer.core.BeanContainer;

/**
 * AOT engine web route TCK.
 */
public class AotWebRouteTest extends AbstractWebRouteTCK {

	@Override
	protected BeanContainer createContext() {
		try {

			return summer.test.TestContainerBuilder.buildAot(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
