package summer.tck.di.replaces;

import summer.core.Component;
import summer.core.annotation.Replaces;

/**
 * Replaces {@link OriginalComponent} in the application context.
 */
@Replaces(OriginalComponent.class)
@Component
public class ReplacementComponent implements ReplacableService {

	@Override
	public String serve() {
		return "replacement";
	}
}
