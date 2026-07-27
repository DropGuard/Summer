package com.github.dropguard.summer.web.server;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.web.RouterRegistry;
import com.github.dropguard.summer.web.ServerConfig;

/**
 * Configuration for Netty server infrastructure beans.
 *
 * <p>Provides {@link NettyServerRunner} and {@link NettyWebSocketBroadcaster}.
 */
@Configuration
public class NettyServerConfiguration {

    @Bean
    public NettyServerRunner nettyServerRunner(RouterRegistry routerRegistry, ServerConfig config) {
        return new NettyServerRunner(routerRegistry, config);
    }

    @Bean
    public NettyWebSocketBroadcaster webSocketBroadcaster() {
        return new NettyWebSocketBroadcaster();
    }
}
