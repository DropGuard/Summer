package summer.scanner.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import summer.aop.MethodInterceptor;
import summer.aop.ProxyFactory;

final class RuntimeAopProcessor {

	private RuntimeAopProcessor() {
	}

	static Object applyProxy(Object instance, Class<?> clazz, List<MethodInterceptor> allInterceptors,
			Map<Class<?>, Object> singletons) {
		if (clazz.getInterfaces().length == 0 || instance instanceof MethodInterceptor) {
			return instance;
		}

		List<MethodInterceptor> matching = allInterceptors.stream().filter(Objects::nonNull)
				.filter(interceptor -> interceptor.supports(clazz)).toList();

		if (matching.isEmpty()) {
			return instance;
		}

		Object proxy = ProxyFactory.createProxy(instance, matching);
		for (Class<?> iface : clazz.getInterfaces()) {
			singletons.put(iface, proxy);
		}
		return proxy;
	}
}
