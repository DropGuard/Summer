package summer.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.BeanContainer;
import summer.core.bean.RouteInfo;
import summer.web.Handler;
import summer.web.HttpParameterResolverChain;
import summer.web.HttpRouter;
import summer.web.RouteRegistrar;

/**
 * Route registrar for the Runtime DI engine.
 *
 * <p>
 * Consumes the pre-built {@link RouteInfo} list from
 * {@link BeanContainer#routes()} — routes are already collected during the
 * container construction phase by {@link RuntimeBeanAdapter#collectRoutes}. The
 * handler {@link java.lang.reflect.Method} is resolved here, inside the runtime
 * module (the only place permitted to hold a {@code Method}), by name lookup on
 * the controller class — no extra state is carried on the shared
 * {@link RouteInfo} type.
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
			Handler handler = HandlerFactory.create(controller, resolveHandler(route), resolverChain);
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
	 * Resolves the handler {@link java.lang.reflect.Method} from the controller
	 * class and method name. The controller class is always present on the runtime
	 * path (it is the discovered {@code @Component}); the method name is the
	 * cross-engine string contract shared with the AOT engine.
	 */
	private static java.lang.reflect.Method resolveHandler(RouteInfo route) {
		for (java.lang.reflect.Method m : route.controllerType.getMethods()) {
			if (m.getName().equals(route.methodName)) {
				return m;
			}
		}
		throw new IllegalStateException("Handler method not found: " + route.controllerClass + "." + route.methodName);
	}
}
