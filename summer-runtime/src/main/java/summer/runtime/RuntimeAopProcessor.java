package summer.runtime;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import summer.aop.MethodInterceptor;
import summer.core.exception.BeanCreationException;

/**
 * Applies AOP proxies to bean instances. Matching interceptors are pre-filtered
 * by {@link BeanDefinitionFactory} (the single source of truth for binding
 * matches).
 */
final class RuntimeAopProcessor {

	private RuntimeAopProcessor() {
	}

	static Object applyProxy(Object instance, Class<?> clazz, List<MethodInterceptor> matchingInterceptors,
			Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings) {
		if (instance == null || instance.getClass().isAnnotationPresent(summer.aop.Interceptor.class)) {
			return instance;
		}

		List<MethodInterceptor> matching = matchingInterceptors.stream().filter(Objects::nonNull).toList();

		if (matching.isEmpty()) {
			return instance;
		}

		// Summer uses JDK dynamic proxies -- requires at least one interface.
		if (clazz.getInterfaces().length == 0) {
			throw new BeanCreationException(clazz.getName()
					+ " is annotated with AOP bindings but implements no interfaces. "
					+ "Summer uses JDK dynamic proxies -- extract an interface and inject it by the interface type instead.");
		}

		return ProxyFactory.createProxy(instance, matching, interceptorBindings);
	}
}
