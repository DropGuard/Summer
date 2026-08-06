package com.github.dropguard.summer.web;

/**
 * Contract for components that contribute routes to a {@link WsRouter}.
 *
 * <p>The framework collects all {@code WsRouteProvider} beans and invokes their {@link
 * #provide(WsRouter.Builder)} method before building the final {@link WsRouter}. This is the
 * WebSocket counterpart of {@link RouteRegistrar}.
 */
public interface WsRouteProvider {

    /**
     * Registers WebSocket routes on the given builder.
     *
     * @param builder the builder to register routes on
     */
    void provide(WsRouter.Builder builder);
}
