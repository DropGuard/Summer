mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
@Internal
mport com.github.dropguard.summer.core.Internal;
 * Interface for registering exception handlers into an {@link ExceptionRegistry}. Allows switching
mport com.github.dropguard.summer.core.Internal;
 * between reflection-based runtime discovery and static AOT registration.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public interface ExceptionHandlerRegistrar {
mport com.github.dropguard.summer.core.Internal;
    void registerHandlers(
mport com.github.dropguard.summer.core.Internal;
            ExceptionRegistry registry, com.github.dropguard.summer.core.BeanContainer context);
mport com.github.dropguard.summer.core.Internal;
}
