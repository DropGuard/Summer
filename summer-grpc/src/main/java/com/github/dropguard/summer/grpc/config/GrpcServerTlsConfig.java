package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;

/**
 * gRPC server-side TLS configuration bound from {@code application.yml}.
 *
 * <p>Example YAML:
 *
 * <pre>{@code
 * grpc:
 *   server:
 *     tls:
 *       enabled: true
 *       cert-chain: /path/to/server.crt
 *       private-key: /path/to/server.key
 * }</pre>
 *
 * <p>Server identity: the certificate chain ({@code certChain}) and private key ({@code
 * privateKey}) the server presents to clients. Client-side trust ({@code trustCert}) lives in
 * {@link GrpcClientTlsConfig} — one application can be a TLS server without ever being a TLS
 * client, so the two roles are configured separately.
 *
 * @param enabled whether TLS is enabled for the gRPC server
 * @param certChain path to the server certificate chain (PEM format)
 * @param privateKey path to the server private key (PEM format)
 */
@ConfigMapping(prefix = "grpc.server.tls")
public interface GrpcServerTlsConfig {

    @WithDefault("false")
    Boolean enabled();

    String certChain();

    String privateKey();
}
