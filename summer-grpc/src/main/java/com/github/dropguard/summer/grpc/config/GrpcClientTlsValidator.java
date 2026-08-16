package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.validation.ConfigValidationException;
import com.github.dropguard.summer.core.validation.Validator;

/**
 * Validates gRPC client-side TLS configuration after binding.
 *
 * <p>Ensures that when client TLS is enabled, the trust anchor is provided. Without a CA
 * certificate the client cannot verify the server's identity and would otherwise silently fall back
 * to plaintext — a data-exposure hazard, so the missing {@code trust-cert} must fail fast instead.
 */
@Configuration
public class GrpcClientTlsValidator implements Validator<GrpcClientTlsConfig> {

    @Override
    public Class<GrpcClientTlsConfig> targetType() {
        return GrpcClientTlsConfig.class;
    }

    @Override
    public void validate(GrpcClientTlsConfig config) {
        if (config.enabled() == null || !config.enabled()) {
            return;
        }
        if (config.trustCert() == null) {
            throw new ConfigValidationException(
                    "TLS enabled but 'grpc.client.tls.trust-cert' is required");
        }
    }
}
