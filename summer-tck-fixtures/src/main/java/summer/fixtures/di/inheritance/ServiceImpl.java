package summer.fixtures.di.inheritance;

import summer.core.Component;

@Component
public class ServiceImpl implements ExtendedService {

	@Override
	public String serve() {
		return "base";
	}

	@Override
	public String extendedServe() {
		return "extended";
	}
}
