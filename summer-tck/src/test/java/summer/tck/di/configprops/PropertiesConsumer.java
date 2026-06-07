package summer.tck.di.configprops;

import summer.core.Component;

/**
 * Test fixture: a @Component that directly depends on auto-bound config
 * properties.
 */
@Component
public class PropertiesConsumer {

	private final AppProperties properties;

	public PropertiesConsumer(AppProperties properties) {
		this.properties = properties;
	}

	public AppProperties getProperties() {
		return properties;
	}
}
