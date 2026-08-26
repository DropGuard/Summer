package com.github.dropguard.summer.core.spi;

/**
 * SPI for registering routes discovered by extensions (e.g., summer-web).
 *
 * <p>Engines (Runtime, AOT) load all available {@code RouteRegistrar} implementations via {@link
 * java.util.ServiceLoader} and invoke {@link #register(RouteRegistry)} during container build,
 * giving each extension a chance to contribute route definitions.
 *
 * <p>This is the core mechanism that decouples the DI container from any specific web framework.
 * The container knows only about {@link RouteRegistry}; extensions provide the scanning and binding
 * logic for their own annotation contracts.
 */
public interface RouteRegistrar {

    /**
     * Register routes into the given registry, scanning the provided bean definitions.
     *
     * @param registry the route registry to populate
     * @param beans the list of bean definitions to scan for routes
     */
    void register(
            RouteRegistry registry,
            java.util.List<com.github.dropguard.summer.core.bean.BeanDefinition> beans);
}
