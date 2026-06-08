package summer.tck.di.configprops;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Test fixture: configuration that depends on auto-bound TlsProperties. Entry
 * point for testing @ConfigurationProperties with non-bean constructor params.
 */
@Configuration
public class TlsConfig {

	@Bean
	public TlsService tlsService(TlsProperties properties) {
		return new TlsService(properties);
	}
}
