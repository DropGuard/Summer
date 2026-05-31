package summer.tck.di.inheritance;

import summer.core.Component;

/**
 * Implementation of ExtendedService (which extends BaseService).
 * This tests whether the DI container can resolve BaseService -> ServiceImpl.
 */
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
