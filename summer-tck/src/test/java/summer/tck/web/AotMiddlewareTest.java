package summer.tck.web;

import summer.core.BeanContainer;

/**
 * AOT engine middleware TCK. The global middleware chain is assembled inside
 * {@link AbstractMiddlewareTCK} from the test universe's {@code @Component}
 * middlewares, so the AOT container (compiled over the same wide universe) is
 * sufficient — no manual bean registration needed.
 */
public class AotMiddlewareTest extends AbstractMiddlewareTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			return summer.test.TestContainerBuilder.buildAot();
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
