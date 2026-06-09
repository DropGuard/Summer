package summer.tck.di.root;

/**
 * Test fixture: service that receives auto-bound root properties via
 * constructor injection.
 */
public class RootService {

	private final RootProperties properties;

	public RootService(RootProperties properties) {
		this.properties = properties;
	}

	public RootProperties getProperties() {
		return properties;
	}
}
