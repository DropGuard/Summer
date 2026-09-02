package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpParameterResolver;
import com.github.dropguard.summer.web.HttpParameterResolverChain;
import com.github.dropguard.summer.web.exception.HandlerInvocationException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Function;

/** Creates {@link Handler}s from controller or exception-handler methods. */
final class HandlerFactory {

    private HandlerFactory() {}

    /**
     * Resolves the {@link Method} to dispatch through for a bean's registered form.
     *
     * <p>Framework registration hooks (routes, exception handlers) receive the bean's ONE legal
     * form from the instantiation record — for an AOP-bound bean that is the proxy. A JDK proxy can
     * only be reflectively invoked on its interface methods, so when the bean is proxied the method
     * is re-resolved on the proxy's interfaces; a handler or route method that is not exposed on
     * any interface fails fast at startup — declare it on the bean's interface (the same rule
     * constructor injection already follows). The declaring interface must also be public: the
     * proxy's invocation handler and the generated AOT adapters invoke the method from other
     * packages, and reflective access checks the declaring class's visibility. Non-proxied beans
     * dispatch on the concrete class as before.
     */
    static Method resolveDispatchMethod(
            Object registered, Class<?> declaredOn, String name, int parameterCount) {
        if (java.lang.reflect.Proxy.isProxyClass(registered.getClass())) {
            for (Class<?> iface : registered.getClass().getInterfaces()) {
                for (Method m : iface.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                        requirePublicInterface(iface, declaredOn, name);
                        return m;
                    }
                }
            }
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                    "Method "
                            + declaredOn.getName()
                            + "."
                            + name
                            + "("
                            + parameterCount
                            + " params) is registered on an AOP-bound bean but is not declared on"
                            + " any of its interfaces. Summer proxies are interface-based — move"
                            + " the method onto the bean's interface so it can be dispatched"
                            + " through the proxy.");
        }
        for (Method m : declaredOn.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                return m;
            }
        }
        return null;
    }

    /**
     * Fail-fast guard for proxy dispatch: the interface carrying the dispatched method must be
     * public, or reflective invocation from the proxy's handler (and the generated AOT adapters)
     * fails with an opaque {@code IllegalAccessException} at request time instead of a
     * comprehensible error at startup.
     */
    private static void requirePublicInterface(Class<?> iface, Class<?> declaredOn, String name) {
        if (!java.lang.reflect.Modifier.isPublic(iface.getModifiers())) {
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                    "Method "
                            + declaredOn.getName()
                            + "."
                            + name
                            + " is dispatched through the interface "
                            + iface.getName()
                            + ", which is not public. Proxy dispatch and the generated AOT"
                            + " adapters invoke it from other packages — make the interface"
                            + " public (a public nested interface also works).");
        }
    }

    /** Creates a Handler from a Java reflection method. */
    @SuppressWarnings("unchecked")
    public static Handler create(
            Object instance, Method method, HttpParameterResolverChain resolverChain) {
        method.setAccessible(true);
        Parameter[] params = method.getParameters();

        // Cold-start parsing: Pre-resolve the parameter providers once
        Function<HttpContext, Object>[] paramProviders = new Function[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            HandlerParam handlerParam = new RuntimeHandlerParam(param);
            HttpParameterResolver resolvedResolver = resolverChain.findResolver(handlerParam);
            if (resolvedResolver != null) {
                paramProviders[i] = resolvedResolver.compile(handlerParam);
            } else {
                paramProviders[i] = ctx -> ctx.body(param.getType());
            }
        }

        return ctx -> {
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = paramProviders[i].apply(ctx);
            }
            try {
                method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                // Handler.handle now declares throws Exception, so the cause propagates unwrapped —
                // @ExceptionHandler matching sees the original exception (runtime or checked).
                // Errors pass through untouched (never wrapped in the invocation failure).
                Throwable cause = e.getTargetException();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw (Error) cause;
            } catch (IllegalAccessException e) {
                throw new HandlerInvocationException("Cannot access handler method", e);
            }
        };
    }
}
