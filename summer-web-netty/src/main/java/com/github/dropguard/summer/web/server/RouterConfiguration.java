package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.web.RouterRegistry;
import com.github.dropguard.summer.web.RouterType;
import com.github.dropguard.summer.web.http.MapRouter;
import com.github.dropguard.summer.web.http.RadixTreeHttpRouter;
import com.github.dropguard.summer.web.websocket.MapWsRouter;
import com.github.dropguard.summer.web.websocket.RadixWsRouter;

/**
 * Framework configuration for router factories.
 *
 * <p>
 * Registers the built-in HTTP and WebSocket router implementations into the
 * {@link RouterRegistry}. This configuration is in summer-web-netty because it
 * needs access to all router implementations.
 * </p>
 *
 * <p>
 * To replace a router implementation in tests, use {@code @Replaces} on a
 * custom {@code @Configuration} that provides a different
 * {@link RouterRegistry}.
 * </p>
 */
@Configuration
public class RouterConfiguration {

	@Bean
	public RouterRegistry routerRegistry() {
		RouterRegistry registry = new RouterRegistry();

		// HTTP routers
		registry.registerHttp(RouterType.RADIX_TREE, RadixTreeHttpRouter::new);
		registry.registerHttp(RouterType.MAP, MapRouter::new);

		// WebSocket routers
		registry.registerWs(RouterType.RADIX_TREE, RadixWsRouter::new);
		registry.registerWs(RouterType.MAP, MapWsRouter::new);

		return registry;
	}
}
