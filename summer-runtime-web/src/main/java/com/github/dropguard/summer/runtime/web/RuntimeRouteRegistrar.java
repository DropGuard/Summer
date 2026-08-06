package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.HttpRouter;
import com.github.dropguard.summer.web.RouteRegistrar;
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
public class RuntimeRouteRegistrar implements RouteRegistrar {

    private static final Logger log = LoggerFactory.getLogger(RuntimeRouteRegistrar.class);

    private final HttpParameterResolverChain resolverChain;

    public RuntimeRouteRegistrar(HttpParameterResolverChain resolverChain) {
        this.resolverChain = resolverChain;
    }

    @Override
    public void registerControllers(HttpRouter.Builder builder, BeanContainer context) {
        for (RouteInfo route : context.routes()) {
            Class<?> controllerClass = resolveControllerClass(route);
            Object controller = context.getBean(controllerClass);
            Handler handler =
                    HandlerFactory.create(
                            controller, resolveHandler(route, controllerClass), resolverChain);
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

    /**
     * Resolves the handler {@link java.lang.reflect.Method} from the controller class and method
     * name. The method name is the cross-engine string contract shared with the AOT engine.
     */
    private static java.lang.reflect.Method resolveHandler(
            RouteInfo route, Class<?> controllerClass) {
        // Match by name AND parameter count (the scanner enforces first-param-HttpContext, so a
        // handler has exactly route.params.size() + 1 parameters): name-only matching binds the
        // wrong method when a controller overloads a handler name.
        int expectedParamCount = route.params.size() + 1;
        for (java.lang.reflect.Method m : controllerClass.getMethods()) {
            if (m.getName().equals(route.methodName)
                    && m.getParameterCount() == expectedParamCount) {
                return m;
            }
        }
        throw new IllegalStateException(
                "Handler method not found: "
                        + route.controllerClass
                        + "."
                        + route.methodName
                        + "("
                        + expectedParamCount
                        + " params)");
    }
}
