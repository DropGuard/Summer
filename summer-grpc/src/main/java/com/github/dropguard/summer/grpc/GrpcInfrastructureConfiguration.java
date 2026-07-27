package com.github.dropguard.summer.grpc;

import com.github.dropguard.summer.core.annotation.Bean;
import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.grpc.client.GrpcChannelManager;
import com.github.dropguard.summer.grpc.config.GrpcServerConfig;
import com.github.dropguard.summer.grpc.config.GrpcTlsConfig;
import com.github.dropguard.summer.grpc.server.GrpcServerRunner;

/**
 * Configuration for gRPC infrastructure beans.
 *
 * <p>Provides {@link GrpcChannelManager} for client channel management and {@link GrpcServerRunner}
 * for server lifecycle.
 */
@Configuration
public class GrpcInfrastructureConfiguration {

    @Bean
    public GrpcChannelManager grpcChannelManager(GrpcTlsConfig tlsConfig) {
        return new GrpcChannelManager(tlsConfig);
    }

    @Bean
    public GrpcServerRunner grpcServerRunner(
            GrpcTlsConfig tlsConfig, GrpcServerConfig serverConfig) {
        return new GrpcServerRunner(tlsConfig, serverConfig);
    }
}
