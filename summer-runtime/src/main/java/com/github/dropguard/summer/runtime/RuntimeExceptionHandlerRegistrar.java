package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
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
 */
public class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

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
            Object instance;
            try {
                Class<?> clazz = Class.forName(beanClassName);
                instance = context.getBean(clazz);
            } catch (ClassNotFoundException e) {
                continue;
            }

            for (BeanDefinition.ExceptionHandlerEntry eh : entry.getValue()) {
                Method method =
                        findMethod(instance.getClass(), eh.methodName(), eh.parameterCount());
                if (method == null) {
                    continue;
                }
                Class<?> exClass;
                try {
                    exClass = Class.forName(eh.exceptionClass());
                } catch (ClassNotFoundException e) {
                    continue;
                }
                Handler handler = HandlerFactory.create(instance, method, resolverChain);
                registry.register(exClass.asSubclass(Throwable.class), handler);
            }
        }
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }
}
