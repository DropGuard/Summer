package summer.grpc.config;

import summer.core.config.ConfigurationProperties;

/**
 * gRPC TLS configuration bound from {@code application.yml}.
 *
 * <p>
 * Example YAML:
 * </p>
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
 * @param enabled
 *            whether TLS is enabled ({@code null} if not configured)
 * @param certChain
 *            path to the server certificate chain (PEM format)
 * @param privateKey
 *            path to the server private key (PEM format)
 * @param trustCert
 *            path to the CA certificate for client trust (PEM format)
 */
@ConfigurationProperties(prefix = "grpc.tls")
public record GrpcTlsConfig(Boolean enabled, String certChain, String privateKey, String trustCert) {
}
