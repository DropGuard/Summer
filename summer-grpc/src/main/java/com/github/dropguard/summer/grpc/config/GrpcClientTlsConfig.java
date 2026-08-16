package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * gRPC client-side TLS configuration bound from {@code application.yml}.
 *
 * <p>Example YAML:
 *
 * <pre>{@code
 * grpc:
 *   client:
 *     tls:
 *       enabled: true
 *       trust-cert: /path/to/ca.crt
 * }</pre>
 *
 * <p>Client trust: the CA certificate ({@code trustCert}) the client uses to verify the servers it
 * calls. Server identity ({@code certChain}/{@code privateKey}) lives in {@link
 * GrpcServerTlsConfig} — an application that only calls out (or only serves) configures only the
 * side it needs.
 *
 * @param enabled whether TLS is enabled for outbound gRPC connections
 * @param trustCert path to the CA certificate used to verify server certificates (PEM format)
 */
@ConfigMapping(prefix = "grpc.client.tls")
public interface GrpcClientTlsConfig {

    @WithDefault("false")
    Boolean enabled();

    String trustCert();
}
