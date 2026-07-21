package summer.tck.fixtures.di.errors;

import summer.core.BeanContainer;
import summer.core.Component;

/**
 * Bean that requests the container itself for injection. The container must
 * reject this (UnsupportedInjectionException) — injecting the container into a
 * bean would create a circular bootstrap reference.
 */
@Component
public class SelfInjectingBean {

	private final BeanContainer container;

	public SelfInjectingBean(BeanContainer container) {
		this.container = container;
	}

	public BeanContainer container() {
		return container;
	}
}
