package summer.tck.di.inheritance;

import summer.core.Component;

/**
 * Client that depends on BaseService (not ExtendedService).
 * This tests whether the DI container can resolve BaseService -> ServiceImpl
 * even though ServiceImpl directly implements ExtendedService.
 */
@Component
public class ServiceClient {

	private final BaseService baseService;

	public ServiceClient(BaseService baseService) {
		this.baseService = baseService;
	}

	public BaseService getBaseService() {
		return baseService;
	}
}
