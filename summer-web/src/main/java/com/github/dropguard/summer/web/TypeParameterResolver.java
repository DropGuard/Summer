mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;
@Internal

mport com.github.dropguard.summer.core.Internal;
/** Resolves parameters typed as {@link HttpContext} or {@link Request}. */
mport com.github.dropguard.summer.core.Internal;
public class TypeParameterResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        Class<?> type = param.type();
mport com.github.dropguard.summer.core.Internal;
        return type == HttpContext.class || type == Request.class;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return param.type() == HttpContext.class ? ctx : ctx.request();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        boolean isCtx = param.type() == HttpContext.class;
mport com.github.dropguard.summer.core.Internal;
        return ctx -> isCtx ? ctx : ctx.request();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
