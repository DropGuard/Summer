package com.github.dropguard.summer.grpc.config;

import com.github.dropguard.summer.core.annotation.Configuration;
import com.github.dropguard.summer.core.validation.ValidationException;
import com.github.dropguard.summer.core.validation.Validator;

/**
 * Validates gRPC TLS configuration after binding.
 *
 * <p>
 * Ensures that when TLS is enabled, the required certificate paths are
 * provided.
 * </p>
 */
@Configuration
public class GrpcTlsValidator implements Validator<GrpcTlsConfig> {

	@Override
	public Class<GrpcTlsConfig> targetType() {
		return GrpcTlsConfig.class;
	}

	@Override
	public void validate(GrpcTlsConfig config) {
		if (config.enabled() == null || !config.enabled()) {
			return;
		}
		if (config.certChain() == null) {
			throw new ValidationException("TLS enabled but 'grpc.tls.cert-chain' is required");
		}
		if (config.privateKey() == null) {
			throw new ValidationException("TLS enabled but 'grpc.tls.private-key' is required");
		}
	}
}
