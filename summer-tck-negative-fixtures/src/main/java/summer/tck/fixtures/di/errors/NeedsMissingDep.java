package summer.tck.fixtures.di.errors;

import summer.core.Component;

/**
 * Bean whose constructor requires a dependency that is never registered. The
 * container must fail (NoSuchBeanException / BeanCreationException) rather than
 * silently wiring a null.
 */
@Component
public class NeedsMissingDep {

	private final MissingDep dep;

	public NeedsMissingDep(MissingDep dep) {
		this.dep = dep;
	}

	public MissingDep dep() {
		return dep;
	}
}
