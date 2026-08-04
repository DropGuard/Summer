package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;
import java.util.List;

/** Immutable server configuration bound from {@code application.yml}. */
@ConfigMapping(prefix = "server")
public interface ServerConfig {

    @WithDefault("8080")
    Integer port();

    @WithDefault("60000")
    Integer idleTimeout();

    @WithDefault("10485760")
    Integer maxBodySize();

    @WithDefault("10000")
    Integer readTimeout();

    List<String> allowedOrigins();

    @WithDefault("65536")
    Integer maxWebSocketFrameSize();

    @WithDefault("RADIX_TREE")
    RouterType routerType();
}
