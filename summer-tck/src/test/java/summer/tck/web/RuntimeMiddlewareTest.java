package summer.tck.web;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.tck.web.dummy.UserController;

public class RuntimeMiddlewareTest extends AbstractMiddlewareTCK {

	@Override
	protected ApplicationContext createContext() {
		// Scan the dummy package to discover all controllers and middlewares
		return RuntimeApplicationContext.create(UserController.class);
	}
}


