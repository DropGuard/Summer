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
