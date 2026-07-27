package com.github.dropguard.summer.web;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for router factory functions.
 *
 * <p>This is a framework infrastructure bean managed by {@code RouterConfiguration}. Router
 * implementations register their factories via the {@link #registerHttp} and {@link #registerWs}
 * methods.
 *
 * <p>The registry is injected into {@link HttpRouter.Builder} and {@link WsRouter.Builder} to
 * create routers based on {@link RouterType}.
 *
 * @see RouterType
 */
public final class RouterRegistry {

    private final Map<RouterType, Function<List<HttpRouter.Builder.Route>, HttpRouter>>
            httpFactories = new EnumMap<>(RouterType.class);
    private final Map<RouterType, Function<List<WsRouter.WsRoute>, WsRouter>> wsFactories =
            new EnumMap<>(RouterType.class);

    /**
     * Registers an HTTP router factory for the given type.
     *
     * @param type the router type
     * @param factory the factory function
     */
    public void registerHttp(
            RouterType type, Function<List<HttpRouter.Builder.Route>, HttpRouter> factory) {
        httpFactories.put(type, factory);
    }

    /**
     * Registers a WebSocket router factory for the given type.
     *
     * @param type the router type
     * @param factory the factory function
     */
    public void registerWs(RouterType type, Function<List<WsRouter.WsRoute>, WsRouter> factory) {
        wsFactories.put(type, factory);
    }

    /**
     * Gets the HTTP router factory for the given type.
     *
     * @param type the router type
     * @return the factory function
     * @throws IllegalArgumentException if no factory is registered for the type
     */
    public Function<List<HttpRouter.Builder.Route>, HttpRouter> httpFactory(RouterType type) {
        Function<List<HttpRouter.Builder.Route>, HttpRouter> factory = httpFactories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "No HTTP router factory registered for type: " + type);
        }
        return factory;
    }

    /**
     * Gets the WebSocket router factory for the given type.
     *
     * @param type the router type
     * @return the factory function
     * @throws IllegalArgumentException if no factory is registered for the type
     */
    public Function<List<WsRouter.WsRoute>, WsRouter> wsFactory(RouterType type) {
        Function<List<WsRouter.WsRoute>, WsRouter> factory = wsFactories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "No WebSocket router factory registered for type: " + type);
        }
        return factory;
    }
}
