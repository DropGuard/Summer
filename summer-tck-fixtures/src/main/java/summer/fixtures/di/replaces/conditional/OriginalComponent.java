package summer.fixtures.di.replaces.conditional;

import summer.core.Component;

@Component
public class OriginalComponent implements ReplacableService {

	@Override
	public String serve() {
		return "original";
	}
}
