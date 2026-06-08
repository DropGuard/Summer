package summer.grpc.config;

import summer.core.Component;
import summer.core.validation.ValidationException;
import summer.core.validation.Validator;

/**
 * Validates gRPC TLS configuration after binding.
 *
 * <p>
 * Ensures that when TLS is enabled, the required certificate paths are provided.
 * </p>
 */
@Component
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
