package summer.tck.web.router;

import summer.web.http.MapRouter;
import summer.web.HttpRouter;

/**
 * TCK tests for MapRouter implementation.
 */
public class MapRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected HttpRouter createRouter() {
		return new MapRouter();
	}
}
