package summer.tck.web;

import summer.core.BeanContainer;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.web.dummy.UserController;
import summer.core.Engine;

public class RuntimeMiddlewareTest extends AbstractMiddlewareTCK {

	@Override
	protected BeanContainer createContext() {
		return RuntimeApplicationContext.create(summer.core.Engine.RUNTIME);
	}
}
