package summer.runtime;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.core.bean.RouteInfo;
import summer.web.Handler;
import summer.web.HttpRouter;
import summer.web.RouteRegistrar;

/**
 * Route registrar for the Runtime DI engine.
 *
 * <p>
 * Consumes the pre-built {@link RouteInfo} list from
 * {@link BeanContainer#routes()} — routes are already collected during the
 * container construction phase by {@link RuntimeBeanAdapter#collectRoutes}.
 * No Jandex or annotation re-scanning is performed.
 * </p>
 */
public class RuntimeRouteRegistrar implements RouteRegistrar {

	private static final Logger log = LoggerFactory.getLogger(RuntimeRouteRegistrar.class);

	private final HttpParameterResolverChain resolverChain;
	private final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();

	public RuntimeRouteRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
		String previousClass = "";
		Class<?> clazz = null;
		Object controller = null;

		for (RouteInfo route : context.routes()) {
			// Load controller class on first encounter, then reuse until class changes
			if (!route.controllerClass.equals(previousClass)) {
				try {
					clazz = Class.forName(route.controllerClass);
					controller = context.getBean(clazz);
				} catch (ClassNotFoundException e) {
					log.warn("[Summer] Failed to load controller class: {}", route.controllerClass);
					clazz = null;
					controller = null;
				}
				previousClass = route.controllerClass;
			}

			if (controller == null) {
				continue;
			}

			Method targetMethod = resolveMethod(clazz, route.methodName);
			if (targetMethod == null) {
				log.warn("[Summer] Method not found: {}.{}", route.controllerClass, route.methodName);
				continue;
			}

			Handler handler = HandlerFactory.create(controller, targetMethod, resolverChain);
			switch (route.httpMethod) {
				case "GET" -> builder.get(route.path, handler);
				case "POST" -> builder.post(route.path, handler);
				case "PUT" -> builder.put(route.path, handler);
				case "DELETE" -> builder.delete(route.path, handler);
				default -> log.warn("[Summer] Unknown HTTP method: {}", route.httpMethod);
			}
		}
	}

	/**
	 * Resolves a Java Method by name for the given class, using a per-class cache
	 * to avoid repeated reflection.
	 */
	private Method resolveMethod(Class<?> clazz, String methodName) {
		return methodCache.computeIfAbsent(clazz, c -> {
			Map<String, Method> map = new HashMap<>();
			for (Method m : c.getMethods()) {
				map.putIfAbsent(m.getName(), m);
			}
			return map;
		}).get(methodName);
	}
}
