package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * gRPC server configuration bound from {@code application.yml}.
 *
 * <pre>{@code
 * grpc:
 *   server:
 *     port: 9090
 * }</pre>
 *
 * @param port the port the gRPC server listens on
 */
@ConfigMapping(prefix = "grpc.server")
public interface GrpcServerConfig {

    @WithDefault("9090")
    Integer port();
}
