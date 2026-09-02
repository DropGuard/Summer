package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.RouterAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Route registrar for the Runtime DI engine.
 *
 * <p>Consumes the pre-built {@link RouteInfo} list from {@link BeanContainer#routes()} — routes are
 * already collected during the container construction phase by the SPI layer (see {@code
 * com.github.dropguard.summer.core.spi.RouteRegistrarLoader}, fed by this module's {@code
 * WebRouteScanner}). The handler {@link java.lang.reflect.Method} is resolved here, inside the
 * runtime-web module (the only web-aware runtime layer permitted to hold a {@code Method}), by name
 * lookup on the controller class — no extra state is carried on the shared {@link RouteInfo} type.
 */
@Internal
public class RuntimeRouteRegistrar implements RouterAdapter {

    private static final Logger log = LoggerFactory.getLogger(RuntimeRouteRegistrar.class);

    private final HttpParameterResolverChain resolverChain;
    private final com.github.dropguard.summer.runtime.InstantiatedBeans instantiated;

    public RuntimeRouteRegistrar(
            HttpParameterResolverChain resolverChain,
            com.github.dropguard.summer.runtime.InstantiatedBeans instantiated) {
        this.resolverChain = resolverChain;
        this.instantiated = instantiated;
    }

    @Override
    public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
        for (RouteInfo route : context.routes()) {
            Class<?> controllerClass = resolveControllerClass(route);
            // Birth record, not getBean: an AOP-bound controller's concrete-class lookup fails
            // loudly by contract, and route dispatch must go through the bean's single legal
            // incarnation (the proxy) so interception applies. resolveDispatchMethod picks the
            // interface method for proxy incarnations and fails fast when the route method is
            // not exposed on any interface.
            Object controller = instantiated.instanceOf(route.controllerClass);
            Handler handler =
                    HandlerFactory.create(
                            controller,
                            HandlerFactory.resolveDispatchMethod(
                                    controller,
                                    controllerClass,
                                    route.methodName,
                                    route.params.size() + 1),
                            resolverChain);
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
     * Resolves the controller {@link Class} from the cross-engine string contract ({@code
     * controllerClass}). The AOT engine emits static handler calls from the same string; the
     * runtime engine resolves the reflective {@code Method} here.
     */
    private static Class<?> resolveControllerClass(RouteInfo route) {
        try {
            return Class.forName(route.controllerClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Controller class not on classpath: " + route.controllerClass, e);
        }
    }
}
