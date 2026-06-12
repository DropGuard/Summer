package summer.web.server;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.web.RouterRegistry;
import summer.web.ServerConfig;
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
	public NettyServerRunner nettyServerRunner(RouterRegistry routerRegistry, ServerConfig config) {
		return new NettyServerRunner(routerRegistry, config);
	}

	@Bean
	public WebSocketBroadcaster webSocketBroadcaster() {
		return new NettyWebSocketBroadcaster();
	}
}
