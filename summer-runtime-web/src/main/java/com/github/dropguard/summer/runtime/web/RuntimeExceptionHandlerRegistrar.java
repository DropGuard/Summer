package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.runtime.InstantiatedBeans;
import com.github.dropguard.summer.web.ExceptionHandlerRegistrar;
import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Exception handler registrar that reads pre-computed {@link BeanDefinition.ExceptionHandlerEntry}
 * records plus the bean birth record ({@link InstantiatedBeans}) rather than re-scanning
 * annotations or routing through {@code getBean}.
 *
 * <p>The registrar consumes the instantiation record because it needs the bean ITSELF: under the
 * one-bean-one-form AOP contract, a bound bean's concrete-class lookup fails loudly, and even a
 * successful proxy lookup could only be invoked on interface methods. The record hands over the
 * bean's single legal form (the proxy for bound beans); {@link
 * HandlerFactory#resolveDispatchMethod} then picks the dispatchable {@code Method} and fails fast
 * when a bound bean's handler method is not exposed on its interfaces.
 *
 * <p>Metadata drift (an unloadable bean/exception class, a handler method that vanished from the
 * class, a bean recorded at discovery but never instantiated) fails fast at startup instead of
 * silently dropping handlers.
 */
class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

    private final HttpParameterResolverChain resolverChain;
    private final Map<String, List<BeanDefinition.ExceptionHandlerEntry>> handlers;
    private final InstantiatedBeans instantiated;

    public RuntimeExceptionHandlerRegistrar(
            HttpParameterResolverChain resolverChain, InstantiatedBeans instantiated) {
        this.resolverChain = resolverChain;
        this.handlers = instantiated.exceptionHandlerEntries();
        this.instantiated = instantiated;
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
            Object instance = instantiated.instanceOf(beanClassName);

            for (BeanDefinition.ExceptionHandlerEntry eh : entry.getValue()) {
                Method method = resolveMethod(clazz, instance, eh);
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

    private static Method resolveMethod(
            Class<?> clazz, Object instance, BeanDefinition.ExceptionHandlerEntry eh) {
        Method method =
                HandlerFactory.resolveDispatchMethod(
                        instance, clazz, eh.methodName(), eh.parameterCount());
        if (method == null) {
            throw new BeanCreationException(
                    "Exception handler method "
                            + clazz.getName()
                            + "."
                            + eh.methodName()
                            + "("
                            + eh.parameterCount()
                            + " params) recorded at discovery no longer exists on the"
                            + " class — stale metadata.");
        }
        return method;
    }
}
