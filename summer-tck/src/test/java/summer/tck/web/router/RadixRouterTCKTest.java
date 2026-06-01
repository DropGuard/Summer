package summer.tck.web.router;

import summer.web.RadixRouter;
import summer.web.Router;

/**
 * TCK tests for RadixRouter implementation.
 */
public class RadixRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected Router createRouter() {
		return new RadixRouter();
	}
}
