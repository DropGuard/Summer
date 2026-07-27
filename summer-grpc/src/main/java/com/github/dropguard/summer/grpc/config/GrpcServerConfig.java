package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.core.config.DefaultValue;

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
@ConfigurationProperties(prefix = "grpc.server")
public record GrpcServerConfig(@DefaultValue("9090") Integer port) {}
