package com.github.dropguard.summer.grpc;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.config.ShutdownConfig;
import com.github.dropguard.summer.grpc.client.GrpcChannelManager;
import com.github.dropguard.summer.grpc.config.GrpcClientTlsConfig;
import com.github.dropguard.summer.grpc.config.GrpcServerConfig;
import com.github.dropguard.summer.grpc.config.GrpcServerTlsConfig;
import com.github.dropguard.summer.grpc.server.GrpcServerRunner;

/**
 * Configuration for gRPC infrastructure beans.
 *
 * <p>Provides {@link GrpcChannelManager} for client channel management and {@link GrpcServerRunner}
 * for server lifecycle. Server TLS ({@link GrpcServerTlsConfig}) and client TLS ({@link
 * GrpcClientTlsConfig}) are configured independently — an application may be a TLS server without
 * ever being a TLS client, and vice versa.
 */
@Configuration
@Internal
public class GrpcInfrastructureConfiguration {

    @Bean
    public GrpcChannelManager grpcChannelManager(GrpcClientTlsConfig tlsConfig) {
        return new GrpcChannelManager(tlsConfig);
    }

    @Bean
    public GrpcServerRunner grpcServerRunner(
            GrpcServerTlsConfig tlsConfig,
            GrpcServerConfig serverConfig,
            ShutdownConfig shutdownConfig) {
        return new GrpcServerRunner(tlsConfig, serverConfig, shutdownConfig);
    }
}
