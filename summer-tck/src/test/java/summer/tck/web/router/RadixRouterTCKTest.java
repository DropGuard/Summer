package summer.tck.web.router;

import java.util.List;
import java.util.function.Function;
import summer.web.HttpRouter;
import summer.web.http.RadixTreeHttpRouter;

/**
 * TCK tests for RadixTreeHttpRouter implementation.
 */
public class RadixRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected Function<List<HttpRouter.Builder.Route>, HttpRouter> routerFactory() {
		return RadixTreeHttpRouter::new;
	}
}
