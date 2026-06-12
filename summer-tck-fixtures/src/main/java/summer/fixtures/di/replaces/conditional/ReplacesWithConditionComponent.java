package summer.fixtures.di.replaces.conditional;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;

@ConditionalOnBean(NonExistentMarker.class)
@Replaces(OriginalComponent.class)
@Component
public class ReplacesWithConditionComponent implements ReplacableService {

	@Override
	public String serve() {
		return "conditional-replacement";
	}
}
