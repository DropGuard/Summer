mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.EnumMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.function.Function;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Registry for router factory functions.
mport com.github.dropguard.summer.core.Internal;
@Internal
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is a framework infrastructure bean managed by {@code RouterConfiguration}. Router
mport com.github.dropguard.summer.core.Internal;
 * implementations register their factories via the {@link #registerHttp} and {@link #registerWs}
mport com.github.dropguard.summer.core.Internal;
 * methods.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>The registry is injected into {@link HttpRouter.Builder} and {@link WsRouter.Builder} to
mport com.github.dropguard.summer.core.Internal;
 * create routers based on {@link RouterType}.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * @see RouterType
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class RouterRegistry {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final Map<RouterType, Function<List<HttpRouter.Builder.Route>, HttpRouter>>
mport com.github.dropguard.summer.core.Internal;
            httpFactories = new EnumMap<>(RouterType.class);
mport com.github.dropguard.summer.core.Internal;
    private final Map<RouterType, Function<List<WsRouter.WsRoute>, WsRouter>> wsFactories =
mport com.github.dropguard.summer.core.Internal;
            new EnumMap<>(RouterType.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Registers an HTTP router factory for the given type.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param type the router type
mport com.github.dropguard.summer.core.Internal;
     * @param factory the factory function
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void registerHttp(
mport com.github.dropguard.summer.core.Internal;
            RouterType type, Function<List<HttpRouter.Builder.Route>, HttpRouter> factory) {
mport com.github.dropguard.summer.core.Internal;
        httpFactories.put(type, factory);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Registers a WebSocket router factory for the given type.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param type the router type
mport com.github.dropguard.summer.core.Internal;
     * @param factory the factory function
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void registerWs(RouterType type, Function<List<WsRouter.WsRoute>, WsRouter> factory) {
mport com.github.dropguard.summer.core.Internal;
        wsFactories.put(type, factory);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Gets the HTTP router factory for the given type.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param type the router type
mport com.github.dropguard.summer.core.Internal;
     * @return the factory function
mport com.github.dropguard.summer.core.Internal;
     * @throws IllegalArgumentException if no factory is registered for the type
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public Function<List<HttpRouter.Builder.Route>, HttpRouter> httpFactory(RouterType type) {
mport com.github.dropguard.summer.core.Internal;
        Function<List<HttpRouter.Builder.Route>, HttpRouter> factory = httpFactories.get(type);
mport com.github.dropguard.summer.core.Internal;
        if (factory == null) {
mport com.github.dropguard.summer.core.Internal;
            throw new IllegalArgumentException(
mport com.github.dropguard.summer.core.Internal;
                    "No HTTP router factory registered for type: " + type);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return factory;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Gets the WebSocket router factory for the given type.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param type the router type
mport com.github.dropguard.summer.core.Internal;
     * @return the factory function
mport com.github.dropguard.summer.core.Internal;
     * @throws IllegalArgumentException if no factory is registered for the type
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public Function<List<WsRouter.WsRoute>, WsRouter> wsFactory(RouterType type) {
mport com.github.dropguard.summer.core.Internal;
        Function<List<WsRouter.WsRoute>, WsRouter> factory = wsFactories.get(type);
mport com.github.dropguard.summer.core.Internal;
        if (factory == null) {
mport com.github.dropguard.summer.core.Internal;
            throw new IllegalArgumentException(
mport com.github.dropguard.summer.core.Internal;
                    "No WebSocket router factory registered for type: " + type);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return factory;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
