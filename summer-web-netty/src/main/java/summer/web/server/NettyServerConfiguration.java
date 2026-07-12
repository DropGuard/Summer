package summer.web.server;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.web.RouterRegistry;
import summer.web.ServerConfig;

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
	public NettyServerRunner nettyServerRunner(RouterRegistry routerRegistry, ServerConfig config, summer.core.config.ShutdownConfig shutdownConfig) {
		return new NettyServerRunner(routerRegistry, config, shutdownConfig);
	}

	@Bean
	public NettyWebSocketBroadcaster webSocketBroadcaster() {
		return new NettyWebSocketBroadcaster();
	}
}
