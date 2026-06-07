package summer.web.server;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.web.RouterRegistry;
import summer.web.RouterType;
import summer.web.http.MapRouter;
import summer.web.http.RadixTreeHttpRouter;
import summer.web.websocket.MapWsRouter;
import summer.web.websocket.RadixWsRouter;

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
