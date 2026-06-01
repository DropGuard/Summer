package summer.fixtures.dummy;

import summer.core.Component;

@Component
public class ServiceA {
	private final ServiceB serviceB;

	public ServiceA(ServiceB serviceB) {
		this.serviceB = serviceB;
	}

	public ServiceB getServiceB() {
		return serviceB;
	}
}
