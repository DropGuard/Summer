package summer.runtime;

import java.util.List;
import java.util.Objects;
import summer.aop.MethodInterceptor;

/**
 * Applies AOP proxies to bean instances. Matching interceptors are pre-filtered
 * by {@link DependencyGraph} (the single source of truth for binding matches).
 */
final class RuntimeAopProcessor {

	private RuntimeAopProcessor() {
	}

	/**
	 * Creates a proxy for the given instance if matching interceptors are provided.
	 *
	 * @param instance
	 *            the raw bean instance
	 * @param clazz
	 *            the bean class
	 * @param matchingInterceptors
	 *            pre-filtered interceptors that match this bean (queried from
	 *            {@link DependencyGraph})
	 * @return the proxy, or the original instance if no interception is needed
	 */
	static Object applyProxy(Object instance, Class<?> clazz, List<MethodInterceptor> matchingInterceptors) {
		if (instance == null || clazz.getInterfaces().length == 0
				|| instance.getClass().isAnnotationPresent(summer.aop.Interceptor.class)) {
			return instance;
		}

		List<MethodInterceptor> matching = matchingInterceptors.stream().filter(Objects::nonNull).toList();

		if (matching.isEmpty()) {
			return instance;
		}

		return ProxyFactory.createProxy(instance, matching);
	}
}
