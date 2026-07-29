mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
@Internal
mport com.github.dropguard.summer.core.Internal;
 * Resolves {@link Throwable}-typed parameters for {@code @ExceptionHandler} methods. Reads the
mport com.github.dropguard.summer.core.Internal;
 * "last_exception" request attribute set by the framework.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class ThrowableResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return Throwable.class.isAssignableFrom(param.type());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return ctx.request().getAttribute(RequestAttributes.LAST_EXCEPTION);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return ctx -> ctx.request().getAttribute(RequestAttributes.LAST_EXCEPTION);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
