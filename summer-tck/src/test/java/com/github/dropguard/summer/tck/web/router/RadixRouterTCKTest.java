package com.github.dropguard.summer.tck.web.router;

import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.http.RadixTreeHttpRouter;
import java.util.List;
import java.util.function.Function;

/**
 * TCK tests for RadixTreeHttpRouter implementation.
 */
public class RadixRouterTCKTest extends AbstractRouterTCK {

	@Override
	protected Function<List<HttpRouter.Builder.Route>, HttpRouter> routerFactory() {
		return RadixTreeHttpRouter::new;
	}
}
