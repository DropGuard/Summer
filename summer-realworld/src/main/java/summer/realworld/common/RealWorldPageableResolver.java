package summer.realworld.common;

import summer.core.Component;
import summer.core.annotation.Replaces;
import summer.web.HttpContext;
import summer.runtime.HttpParameterResolver;
import summer.runtime.DefaultPageResolver;
import java.lang.reflect.Parameter;
import java.util.function.Function;

@Component
@Replaces(DefaultPageResolver.class)
public class RealWorldPageableResolver implements HttpParameterResolver {
    @Override
    public boolean supports(Parameter param) {
        return LimitOffsetPageable.class.isAssignableFrom(param.getType());
    }

    @Override
    public Object resolve(HttpContext ctx, Parameter param) {
        String limitStr = ctx.queryParam("limit");
        String offsetStr = ctx.queryParam("offset");
        int limit = 20;
        int offset = 0;
        try {
            if (limitStr != null) limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException ignored) {}
        try {
            if (offsetStr != null) offset = Integer.parseInt(offsetStr);
        } catch (NumberFormatException ignored) {}
        
        return new LimitOffsetPageable(limit, offset);
    }
    @Override
    public Function<HttpContext, Object> compile(Parameter param) {
        return ctx -> resolve(ctx, null);
    }

    @Override
    public Function<HttpContext, Object> compileAot(Class<?> paramType, String paramName) {
        return ctx -> resolve(ctx, null);
    }
}
