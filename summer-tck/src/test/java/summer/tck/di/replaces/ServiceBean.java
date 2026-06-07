package summer.tck.di.replaces;

/**
 * A simple bean produced by a @Bean method, used to test cascade removal.
 */
public class ServiceBean {

	private final String source;

	public ServiceBean(String source) {
		this.source = source;
	}

	public String source() {
		return source;
	}
}
