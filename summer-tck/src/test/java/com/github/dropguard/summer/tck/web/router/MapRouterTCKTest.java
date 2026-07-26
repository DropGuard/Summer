package com.github.dropguard.summer.tck.web.router;

import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.http.MapRouter;
import java.util.List;
import java.util.function.Function;

/**
 * TCK tests for MapRouter implementation.
 */
public class MapRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected Function<List<HttpRouter.Builder.Route>, HttpRouter> routerFactory() {
		return MapRouter::new;
	}
}
