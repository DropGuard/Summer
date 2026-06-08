package summer.fixtures.validation;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Entry point for validation TCK tests.
 */
@Configuration
public class ValidationConfig {

	@Bean
	public TlsService tlsService(TlsConfig config) {
		return new TlsService(config);
	}
}
