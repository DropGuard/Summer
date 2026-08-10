package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;

/**
 * Resolves {@link Throwable}-typed parameters for {@code @ExceptionHandler} methods. Reads the
 * "last_exception" request attribute set by the framework.
 */
@Internal
public class ThrowableResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return Throwable.class.isAssignableFrom(param.type());
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        return ctx.request().getAttribute(RequestAttributes.LAST_EXCEPTION);
    }
}
