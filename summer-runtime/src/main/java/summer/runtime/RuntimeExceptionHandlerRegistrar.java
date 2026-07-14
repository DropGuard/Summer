package summer.runtime;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import summer.core.BeanContainer;
import summer.core.bean.BeanDefinition;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.Handler;

/**
 * Exception handler registrar that reads from pre-computed
 * {@link BeanDefinition.ExceptionHandlerEntry} records rather than re-scanning
 * annotations via reflection.
 *
 * <p>
 * Pre-computed handler data is set by
 * {@code RuntimeBeanContainerBuilder.initialize()} after discovery — the
 * registrar itself only resolves {@code Method} handles by name and parameter
 * count (no annotation scanning).
 * </p>
 */
public class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

	/**
	 * Pre-computed handler entries keyed by bean qualified name. Populated by
	 * {@link #setPrebuiltHandlers(List)} during container initialization.
	 */
	static volatile Map<String, List<BeanDefinition.ExceptionHandlerEntry>> prebuiltHandlers = Map.of();

	private final HttpParameterResolverChain resolverChain;

	public RuntimeExceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	/**
	 * Pre-computes exception handler metadata from BeanDefinitions. Called by
	 * {@code RuntimeBeanContainerBuilder.initialize()} after discovery.
	 */
	public static void setPrebuiltHandlers(List<BeanDefinition> candidates) {
		Map<String, List<BeanDefinition.ExceptionHandlerEntry>> map = new HashMap<>();
		for (BeanDefinition bean : candidates) {
			if (bean.exceptionHandlerMethods.isEmpty()) {
				continue;
			}
			map.put(bean.qualifiedName, List.copyOf(bean.exceptionHandlerMethods));
		}
		prebuiltHandlers = Map.copyOf(map);
	}

	@Override
	public void registerHandlers(ExceptionRegistry registry, BeanContainer context) {
		Map<String, List<BeanDefinition.ExceptionHandlerEntry>> handlers = prebuiltHandlers;
		if (handlers.isEmpty()) {
			return;
		}

		for (var entry : handlers.entrySet()) {
			String beanClassName = entry.getKey();
			Object instance;
			try {
				Class<?> clazz = Class.forName(beanClassName);
				instance = context.getBean(clazz);
			} catch (ClassNotFoundException e) {
				continue;
			}

			for (BeanDefinition.ExceptionHandlerEntry eh : entry.getValue()) {
				Method method = findMethod(instance.getClass(), eh.methodName(), eh.parameterCount());
				if (method == null) {
					continue;
				}
				Class<?> exClass;
				try {
					exClass = Class.forName(eh.exceptionClass());
				} catch (ClassNotFoundException e) {
					continue;
				}
				Handler handler = HandlerFactory.create(instance, method, resolverChain);
				registry.register(exClass.asSubclass(Throwable.class), handler);
			}
		}
	}

	private static Method findMethod(Class<?> clazz, String name, int paramCount) {
		for (Method m : clazz.getDeclaredMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
				return m;
			}
		}
		return null;
	}
}
