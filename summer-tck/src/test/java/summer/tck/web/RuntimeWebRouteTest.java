package summer.tck.web;

import summer.core.ApplicationContext;
import summer.runtime.RuntimeDiEngine;
import summer.tck.web.dummy.UserController;

public class RuntimeWebRouteTest extends AbstractWebRouteTCK {

	@Override
	protected ApplicationContext createAndInitializeContext() {
		return new RuntimeDiEngine().create(UserController.class);
	}
}
