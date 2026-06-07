package summer.tck.di.replaces.conditional;

import summer.core.Component;

/**
 * Original component for conditional replacement testing.
 */
@Component
public class OriginalComponent implements ReplacableService {

	@Override
	public String serve() {
		return "original";
	}
}
