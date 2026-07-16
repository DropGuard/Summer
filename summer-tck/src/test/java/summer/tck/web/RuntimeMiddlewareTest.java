package summer.tck.web;

import summer.core.BeanContainer;

public class RuntimeMiddlewareTest extends AbstractMiddlewareTCK {
	@Override
	protected BeanContainer createContext() {
		summer.web.GlobalMiddlewareChain chain = new summer.web.GlobalMiddlewareChain(
				java.util.List.of(summer.fixtures.web.dummy.GlobalLoggingMiddleware.class));
		return summer.test.TestContainerBuilder.buildWithExternal(chain);
	}
}
