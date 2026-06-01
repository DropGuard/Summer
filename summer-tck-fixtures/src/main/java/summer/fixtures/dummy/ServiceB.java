package summer.fixtures.dummy;

import summer.core.Component;

@Component
public class ServiceB {
	private final ServiceC serviceC;

	public ServiceB(ServiceC serviceC) {
		this.serviceC = serviceC;
	}

	public ServiceC getServiceC() {
		return serviceC;
	}
}
