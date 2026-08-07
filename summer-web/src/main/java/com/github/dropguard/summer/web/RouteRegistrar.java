package com.github.dropguard.summer.web;

/**
 * Interface for registering routes and controller adapters. Allows switching between
 * reflection-based runtime discovery and static AOT registration.
 *
 * <p>Not to be confused with {@link com.github.dropguard.summer.core.spi.RouteRegistrar} — the
 * engine-level ServiceLoader SPI that extensions implement to contribute route definitions. This
 * one is the user-facing controller-registration contract; the SPI one is the engine-extension
 * contract. Both share the simple name by design (each is the "registrar" of its layer).
 */
public interface RouteRegistrar {
    void registerControllers(
            HttpRouter.Builder builder, com.github.dropguard.summer.core.BeanContainer context);
}
