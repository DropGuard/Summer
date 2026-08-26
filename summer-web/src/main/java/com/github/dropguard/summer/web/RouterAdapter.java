package com.github.dropguard.summer.web;

/**
 * Installs controller routes into an {@link HttpRouter} at startup. This is the engine-product
 * contract: the Runtime engine provides a reflective implementation and the AOT engine generates
 * one at build time; the server runner collects every {@code RouterAdapter} bean and calls {@link
 * #registerControllers} once during boot.
 *
 * <p>To <em>contribute</em> route definitions from an extension (scanning, health endpoints, etc.),
 * implement the separate {@code com.github.dropguard.summer.core.spi.RouteRegistrar} ServiceLoader
 * SPI instead — that contract feeds the cross-engine {@code RouteInfo} metadata this adapter later
 * installs.
 */
public interface RouterAdapter {
    void registerControllers(
            HttpRouter.Builder builder, com.github.dropguard.summer.core.BeanContainer context);
}
