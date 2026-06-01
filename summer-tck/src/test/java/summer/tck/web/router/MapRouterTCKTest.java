package summer.tck.web.router;

import summer.web.MapRouter;
import summer.web.Router;

/**
 * TCK tests for MapRouter implementation.
 */
public class MapRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected Router createRouter() {
		return new MapRouter();
	}
}
