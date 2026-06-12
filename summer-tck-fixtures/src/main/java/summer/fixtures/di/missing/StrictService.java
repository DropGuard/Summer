package summer.fixtures.di.missing;

public class StrictService {
	private final StrictProperties properties;

	public StrictService(StrictProperties properties) {
		this.properties = properties;
	}

	public StrictProperties getProperties() {
		return properties;
	}
}
