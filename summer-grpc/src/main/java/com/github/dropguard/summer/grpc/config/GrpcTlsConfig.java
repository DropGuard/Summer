package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.config.ConfigurationProperties;
import com.github.dropguard.summer.core.config.DefaultValue;

/**
 * gRPC TLS configuration bound from {@code application.yml}.
 *
 * <p>Example YAML:
 *
 * <pre>{@code
 * grpc:
 *   tls:
 *     enabled: true
 *     cert-chain: /path/to/server.crt
 *     private-key: /path/to/server.key
 *     trust-cert: /path/to/ca.crt
 * }</pre>
 *
 * @param enabled whether TLS is enabled
 * @param certChain path to the server certificate chain (PEM format)
 * @param privateKey path to the server private key (PEM format)
 * @param trustCert path to the CA certificate for client trust (PEM format)
 */
@ConfigurationProperties(prefix = "grpc.tls")
public record GrpcTlsConfig(
        @DefaultValue("false") Boolean enabled,
        String certChain,
        String privateKey,
        String trustCert) {}
