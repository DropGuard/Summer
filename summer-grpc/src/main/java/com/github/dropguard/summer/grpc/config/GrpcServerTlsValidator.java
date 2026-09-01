package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;

/**
 * Validates gRPC server-side TLS configuration after binding.
 *
 * <p>Ensures that when server TLS is enabled, the required certificate paths are provided. If the
 * server's identity files are missing, the server must not silently fall back to plaintext.
 */
@Configuration
public class GrpcServerTlsValidator implements Validator<GrpcServerTlsConfig> {

    @Override
    public Class<GrpcServerTlsConfig> targetType() {
        return GrpcServerTlsConfig.class;
    }

    @Override
    public void validate(GrpcServerTlsConfig config, Result result) {
        if (config.enabled() == null || !config.enabled()) {
            return;
        }
        if (config.certChain() == null) {
            result.violate("certChain", "TLS enabled but 'grpc.server.tls.cert-chain' is required");
        }
        if (config.privateKey() == null) {
            result.violate(
                    "privateKey", "TLS enabled but 'grpc.server.tls.private-key' is required");
        }
    }
}
