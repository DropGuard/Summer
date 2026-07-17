package summer.tck.web;

import summer.core.BeanContainer;

/**
 * Runtime engine middleware TCK. The global middleware chain is assembled
 * inside {@link AbstractMiddlewareTCK} from the test universe's
 * {@code @Component} middlewares, so the default wide-universe container is
 * sufficient — no manual bean registration needed.
 */
public class RuntimeMiddlewareTest extends AbstractMiddlewareTCK {
	@Override
	protected BeanContainer createContext() {
		return summer.test.TestContainerBuilder.build();
	}
}
