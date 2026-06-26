package summer.fixtures.di.runtime;

import summer.core.Component;
import summer.core.Provider;

@Component
public class StringProviderComponent implements Provider<String> {
	@Override
	public String provide() {
		return "Hello Provider";
	}
}
