package summer.tck.web.router;

import java.util.List;
import java.util.function.Function;
import summer.web.HttpRouter;
import summer.web.http.MapRouter;

/**
 * TCK tests for MapRouter implementation.
 */
public class MapRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected Function<List<HttpRouter.Builder.Route>, HttpRouter> routerFactory() {
		return MapRouter::new;
	}
}
