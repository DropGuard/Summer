package summer.fixtures.di.replaces;

import summer.core.Component;
import summer.core.annotation.Replaces;

@Replaces(OriginalComponent.class)
@Component
public class ReplacementComponent implements ReplacableService {

	@Override
	public String serve() {
		return "replacement";
	}
}
