package summer.tck.web;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeApplicationContext;
import summer.fixtures.web.dummy.UserController;

public class RuntimeWebRouteTest extends AbstractWebRouteTCK {

	@Override
	protected ApplicationContext createContext() {
		return RuntimeApplicationContext.create();
	}
}
