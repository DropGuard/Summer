package summer.runtime;

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
 * Each {@link RouteInfo} carries the resolved {@link java.lang.reflect.Method}
 * directly, so no re-reflection or method-name matching is performed.
 * </p>
 */
public class RuntimeRouteRegistrar implements RouteRegistrar {

	private static final Logger log = LoggerFactory.getLogger(RuntimeRouteRegistrar.class);

	private final HttpParameterResolverChain resolverChain;

	public RuntimeRouteRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
		for (RouteInfo route : context.routes()) {
			Object controller = context.getBean(route.controllerType);
			Handler handler = HandlerFactory.create(controller, route.handlerMethod, resolverChain);
			switch (route.httpMethod) {
				case "GET" -> builder.get(route.path, handler);
				case "POST" -> builder.post(route.path, handler);
				case "PUT" -> builder.put(route.path, handler);
				case "DELETE" -> builder.delete(route.path, handler);
				default -> log.warn("[Summer] Unknown HTTP method: {}", route.httpMethod);
			}
		}
	}
}
