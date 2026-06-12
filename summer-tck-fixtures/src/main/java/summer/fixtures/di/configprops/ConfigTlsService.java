package summer.fixtures.di.configprops;

/**
 * Test fixture: service that receives auto-bound TlsProperties via @Bean
 * method.
 */
public class ConfigTlsService {

	private final TlsProperties properties;

	public ConfigTlsService(TlsProperties properties) {
		this.properties = properties;
	}

	public TlsProperties getProperties() {
		return properties;
	}
}
