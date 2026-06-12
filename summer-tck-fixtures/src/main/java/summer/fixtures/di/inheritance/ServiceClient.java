package summer.fixtures.di.inheritance;

import summer.core.Component;

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
