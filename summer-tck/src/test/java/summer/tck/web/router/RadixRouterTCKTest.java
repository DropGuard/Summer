package summer.tck.web.router;

import summer.web.http.RadixRouter;
import summer.web.HttpRouter;

/**
 * TCK tests for RadixRouter implementation.
 */
public class RadixRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected HttpRouter createRouter() {
		return new RadixRouter();
	}
}
