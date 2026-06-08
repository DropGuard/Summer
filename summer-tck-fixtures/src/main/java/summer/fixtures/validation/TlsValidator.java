package summer.fixtures.validation;

import summer.core.Component;
import summer.core.validation.ValidationException;
import summer.core.validation.Validator;

/**
 * Test fixture: validates TLS config. Throws when TLS is enabled but certs are
 * missing.
 */
@Component
public class TlsValidator implements Validator<TlsConfig> {

	@Override
	public Class<TlsConfig> targetType() {
		return TlsConfig.class;
	}

	@Override
	public void validate(TlsConfig config) {
		if (config.enabled() != null && config.enabled()) {
			if (config.certChain() == null) {
				throw new ValidationException("TLS enabled but cert-chain is required");
			}
			if (config.privateKey() == null) {
				throw new ValidationException("TLS enabled but private-key is required");
			}
		}
	}
}
