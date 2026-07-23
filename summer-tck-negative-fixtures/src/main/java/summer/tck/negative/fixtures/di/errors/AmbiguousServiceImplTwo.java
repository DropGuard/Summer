package summer.tck.negative.fixtures.di.errors;

import summer.core.Component;

@Component
public class AmbiguousServiceImplTwo implements AmbiguousService {
	@Override
	public String name() {
		return "two";
	}
}
