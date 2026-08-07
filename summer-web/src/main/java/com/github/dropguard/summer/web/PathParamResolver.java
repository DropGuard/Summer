package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.TypeConverter;
import com.github.dropguard.summer.web.annotation.PathParam;

/**
 * Resolves {@link PathParam @PathParam}-annotated parameters from URL path segments. Conversion
 * delegates to {@link TypeConverter} — the single conversion truth shared with {@link
 * QueryParamResolver}, config binding, and the AOT generated adapter, so both engines convert path
 * params identically (enums, float/short/byte/char included).
 */
@Internal
public class PathParamResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return param.hasAnnotation(PathParam.class);
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        return TypeConverter.convert(ctx.request().pathParam(param.bindingName()), param.type());
    }

    @Override
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
        String name = param.bindingName();
        Class<?> targetType = param.type();
        return ctx -> TypeConverter.convert(ctx.request().pathParam(name), targetType);
    }
}
