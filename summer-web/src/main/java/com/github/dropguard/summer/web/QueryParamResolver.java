package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.TypeConverter;
import com.github.dropguard.summer.web.annotation.QueryParam;

/**
 * Resolves {@link QueryParam @QueryParam}-annotated parameters from the URL query string. Uses
 * {@link TypeConverter} for type conversion.
 */
@Internal
public class QueryParamResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return param.hasAnnotation(QueryParam.class);
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        String value = ctx.request().queryParam(param.bindingName());
        return TypeConverter.convert(value, param.type());
    }

    @Override
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
        String name = param.bindingName();
        Class<?> type = param.type();
        return ctx -> TypeConverter.convert(ctx.request().queryParam(name), type);
    }
}
