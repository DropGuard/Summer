mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;
@Internal
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.annotation.PathParam;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/** Resolves {@link PathParam @PathParam}-annotated parameters from URL path segments. */
mport com.github.dropguard.summer.core.Internal;
public class PathParamResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return param.binding() == ParamBinding.PATH;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        String raw = ctx.request().pathParam(param.bindingName());
mport com.github.dropguard.summer.core.Internal;
        return convert(raw, param.type());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        String name = param.bindingName();
mport com.github.dropguard.summer.core.Internal;
        Class<?> targetType = param.type();
mport com.github.dropguard.summer.core.Internal;
        return ctx -> convert(ctx.request().pathParam(name), targetType);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static Object convert(String raw, Class<?> targetType) {
mport com.github.dropguard.summer.core.Internal;
        if (raw == null) {
mport com.github.dropguard.summer.core.Internal;
            return null;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == String.class) {
mport com.github.dropguard.summer.core.Internal;
            return raw;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == Long.class || targetType == long.class) {
mport com.github.dropguard.summer.core.Internal;
            return Long.valueOf(raw);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == Integer.class || targetType == int.class) {
mport com.github.dropguard.summer.core.Internal;
            return Integer.valueOf(raw);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == Boolean.class || targetType == boolean.class) {
mport com.github.dropguard.summer.core.Internal;
            return Boolean.valueOf(raw);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (targetType == Double.class || targetType == double.class) {
mport com.github.dropguard.summer.core.Internal;
            return Double.valueOf(raw);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return raw;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
