package summer.fixtures;

import summer.core.Component;
import summer.core.Provider;

@Component
public class StringProvider implements Provider<String> {
	@Override
	public String provide() {
		return "Hello Provider";
	}
}
