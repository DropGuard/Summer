package summer.tck.di.generic;

import summer.core.Component;

/**
 * Client that depends on GenericService<String>.
 */
@Component
public class GenericServiceClient {

	private final GenericService<String> service;

	public GenericServiceClient(GenericService<String> service) {
		this.service = service;
	}

	public GenericService<String> getService() {
		return service;
	}
}
