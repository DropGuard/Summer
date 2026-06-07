package summer.web.server;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.web.RouterRegistry;
import summer.web.websocket.WebSocketBroadcaster;

/**
 * Configuration for Netty server infrastructure beans.
 *
 * <p>
 * Provides {@link NettyServerRunner} and {@link NettyWebSocketBroadcaster}.
 * </p>
 */
@Configuration
public class NettyServerConfiguration {

	@Bean
	public NettyServerRunner nettyServerRunner(RouterRegistry routerRegistry) {
		return new NettyServerRunner(routerRegistry);
	}

	@Bean
	public WebSocketBroadcaster webSocketBroadcaster() {
		return new NettyWebSocketBroadcaster();
	}
}
