mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
@Internal
mport com.github.dropguard.summer.core.Internal;
 * Interface for registering routes and controller adapters. Allows switching between
mport com.github.dropguard.summer.core.Internal;
 * reflection-based runtime discovery and static AOT registration.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public interface RouteRegistrar {
mport com.github.dropguard.summer.core.Internal;
    void registerControllers(
mport com.github.dropguard.summer.core.Internal;
            HttpRouter.Builder builder, com.github.dropguard.summer.core.BeanContainer context);
mport com.github.dropguard.summer.core.Internal;
}
