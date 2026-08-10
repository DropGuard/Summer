package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.runtime.HandlerMetadata;
import com.github.dropguard.summer.web.ExceptionHandlerRegistrar;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Exception handler registrar that reads from pre-computed {@link
 * BeanDefinition.ExceptionHandlerEntry} records rather than re-scanning annotations via reflection.
 *
 * <p>Pre-computed handler data is set by {@code RuntimeContainer init()} after discovery — the
 * registrar itself only resolves {@code Method} handles by name and parameter count (no annotation
 * scanning).
 *
 * <p>Metadata drift (an unloadable bean/exception class, a handler method that vanished from the
 * class) fails fast at startup instead of silently dropping handlers — the metadata comes from the
 * same Jandex discovery that produced the container's beans, so a miss is stale/corrupt index
 * drift, not a recoverable state.
 */
class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

    private final HttpParameterResolverChain resolverChain;
    private final Map<String, List<BeanDefinition.ExceptionHandlerEntry>> handlers;

    public RuntimeExceptionHandlerRegistrar(
            HttpParameterResolverChain resolverChain, HandlerMetadata handlerMetadata) {
        this.resolverChain = resolverChain;
        this.handlers = handlerMetadata.entries();
    }

    @Override
    public void registerHandlers(ExceptionRegistry registry, BeanContainer context) {
        if (handlers.isEmpty()) {
            return;
        }

        for (var entry : handlers.entrySet()) {
            String beanClassName = entry.getKey();
            Class<?> clazz;
            try {
                clazz = Class.forName(beanClassName);
            } catch (ClassNotFoundException e) {
                throw new BeanCreationException(
                        "Cannot load class "
                                + beanClassName
                                + " for exception-handler registration — the class is in the"
                                + " Jandex index but missing from the classpath (stale index?).",
                        e);
            }
            Object instance = context.getBean(clazz);

            for (BeanDefinition.ExceptionHandlerEntry eh : entry.getValue()) {
                Method method = findMethod(clazz, eh.methodName(), eh.parameterCount());
                if (method == null) {
                    throw new BeanCreationException(
                            "Exception handler method "
                                    + beanClassName
                                    + "."
                                    + eh.methodName()
                                    + "("
                                    + eh.parameterCount()
                                    + " params) recorded at discovery no longer exists on the"
                                    + " class — stale metadata.");
                }
                Class<?> exClass;
                try {
                    exClass = Class.forName(eh.exceptionClass());
                } catch (ClassNotFoundException e) {
                    throw new BeanCreationException(
                            "@ExceptionHandler type "
                                    + eh.exceptionClass()
                                    + " (for "
                                    + beanClassName
                                    + "."
                                    + eh.methodName()
                                    + ") cannot be loaded — the class is in the Jandex index but"
                                    + " missing from the classpath.",
                            e);
                }
                Handler handler = HandlerFactory.create(instance, method, resolverChain);
                registry.register(exClass.asSubclass(Throwable.class), handler);
            }
        }
    }

    /**
     * Finds the handler method on the type discovery recorded it against — the CONCRETE class, not
     * whatever instance {@code context.getBean} returns. (The container registers the raw instance
     * under the concrete-class key and proxies under interface keys only, so the two coincide
     * today; looking up on the concrete class keeps this registrar independent of that registration
     * convention.)
     */
    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }
}
