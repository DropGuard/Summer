package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;
import com.github.dropguard.summer.web.annotation.PathParam;

/** Resolves {@link PathParam @PathParam}-annotated parameters from URL path segments. */
@Internal
public class PathParamResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return param.binding() == ParamBinding.PATH;
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        String raw = ctx.request().pathParam(param.bindingName());
        return convert(raw, param.type());
    }

    @Override
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
        String name = param.bindingName();
        Class<?> targetType = param.type();
        return ctx -> convert(ctx.request().pathParam(name), targetType);
    }

    private static Object convert(String raw, Class<?> targetType) {
        if (raw == null) {
            return null;
        }
        if (targetType == String.class) {
            return raw;
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(raw);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(raw);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(raw);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(raw);
        }
        return raw;
    }
}
