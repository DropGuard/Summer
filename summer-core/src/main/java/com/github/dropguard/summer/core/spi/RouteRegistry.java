package com.github.dropguard.summer.core.spi;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.RouteInfo;

/**
 * Callback interface for route registration during container build.
 *
 * <p>Extensions (e.g., summer-web) implement {@link RouteRegistrar} and call methods on this
 * registry to contribute route definitions. The registry is provided by the engine (Runtime or AOT)
 * and accumulates all routes before the container is finalized.
 *
 * <p>This interface is part of the core SPI and holds no dependency on any web-specific types — it
 * operates on {@link BeanDefinition} and primitive metadata (path, method, handler method name,
 * etc.).
 */
public interface RouteRegistry {

    /**
     * Register a route backed by a handler method on a bean.
     *
     * @param bean the bean definition that contains the handler method
     * @param httpMethod the HTTP method (GET, POST, etc.) as a string
     * @param path the route path pattern
     * @param handlerMethodName the name of the method on {@code bean} to invoke
     * @param parameters the list of parameter bindings for this route
     */
    void registerRoute(
            BeanDefinition bean,
            String httpMethod,
            String path,
            String handlerMethodName,
            java.util.List<RouteInfo.ParamInfo> parameters);

    /**
     * Register an exception handler method on a bean.
     *
     * @param bean the bean definition that contains the handler method
     * @param handlerMethodName the name of the method on {@code bean} to invoke
     * @param exceptionType the fully qualified name of the exception class handled
     * @param parameterCount number of parameters the method accepts (0 or 1)
     */
    void registerExceptionHandler(
            BeanDefinition bean,
            String handlerMethodName,
            String exceptionType,
            int parameterCount);
}
