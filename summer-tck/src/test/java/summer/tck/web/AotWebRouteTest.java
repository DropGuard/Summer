package summer.tck.web;

import summer.core.BeanContainer;

public class AotWebRouteTest extends AbstractWebRouteTCK {

	@Override
	protected BeanContainer createContext() {
		return summer.test.TestContainerBuilder.buildAot();
	}
}
