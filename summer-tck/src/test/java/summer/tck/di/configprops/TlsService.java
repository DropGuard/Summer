package summer.tck.di.configprops;

/**
 * Test fixture: service that receives auto-bound TlsProperties via @Bean
 * method.
 */
public class TlsService {

	private final TlsProperties properties;

	public TlsService(TlsProperties properties) {
		this.properties = properties;
	}

	public TlsProperties getProperties() {
		return properties;
	}
}
