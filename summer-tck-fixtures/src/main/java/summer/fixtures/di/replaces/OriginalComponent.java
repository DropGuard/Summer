package summer.fixtures.di.replaces;

import summer.core.Component;

@Component
public class OriginalComponent implements ReplacableService {

	@Override
	public String serve() {
		return "original";
	}
}
