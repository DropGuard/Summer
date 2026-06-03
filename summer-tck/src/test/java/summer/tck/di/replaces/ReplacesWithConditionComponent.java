package summer.tck.di.replaces;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;

/**
 * Has both {@code @Replaces} and {@code @ConditionalOnBean}.
 * When the condition is NOT met, the replacement should NOT happen —
 * the original component should survive.
 */
@ConditionalOnBean(NonExistentMarker.class)
@Replaces(OriginalComponent.class)
@Component
public class ReplacesWithConditionComponent implements ReplacableService {

	@Override
	public String serve() {
		return "conditional-replacement";
	}
}
