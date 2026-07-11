package summer.tck.web;

import summer.core.BeanContainer;

/**
 * AOT engine middleware TCK.
 */
public class AotMiddlewareTest extends AbstractMiddlewareTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			
			summer.web.GlobalMiddlewareChain chain = new summer.web.GlobalMiddlewareChain(java.util.List.of(summer.fixtures.web.dummy.GlobalLoggingMiddleware.class));
			return summer.test.TestContainerBuilder.buildAot(null, chain);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
