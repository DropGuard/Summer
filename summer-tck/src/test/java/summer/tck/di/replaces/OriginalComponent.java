package summer.tck.di.replaces;

import summer.core.Component;

/**
 * Original component that can be replaced by {@link ReplacementComponent}.
 */
@Component
public class OriginalComponent implements ReplacableService {

	@Override
	public String serve() {
		return "original";
	}
}
