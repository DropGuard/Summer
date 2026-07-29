mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.RouteInfo.ParamBinding;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.TypeConverter;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.web.annotation.QueryParam;
@Internal
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Resolves {@link QueryParam @QueryParam}-annotated parameters from the URL query string. Uses
mport com.github.dropguard.summer.core.Internal;
 * {@link TypeConverter} for type conversion.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class QueryParamResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return param.binding() == ParamBinding.QUERY;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        String value = ctx.request().queryParam(param.bindingName());
mport com.github.dropguard.summer.core.Internal;
        return TypeConverter.convert(value, param.type());
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
        Class<?> type = param.type();
mport com.github.dropguard.summer.core.Internal;
        return ctx -> TypeConverter.convert(ctx.request().queryParam(name), type);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
