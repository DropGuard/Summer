package com.github.dropguard.summer.web;

/**
 * Interface for registering routes and controller adapters. Allows switching between
 * reflection-based runtime discovery and static AOT registration.
 */
public interface RouteRegistrar {
    void registerControllers(
            HttpRouter.Builder builder, com.github.dropguard.summer.core.BeanContainer context);
}
