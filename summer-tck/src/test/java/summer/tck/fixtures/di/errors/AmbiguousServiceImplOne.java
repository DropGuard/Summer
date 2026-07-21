package summer.tck.fixtures.di.errors;

import summer.core.Component;

@Component
public class AmbiguousServiceImplOne implements AmbiguousService {
	@Override
	public String name() {
		return "one";
	}
}
