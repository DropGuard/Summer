package summer.twitter.common;

import summer.core.Component;
import summer.runtime.HttpParameterResolver;
import summer.web.HttpContext;

import java.lang.reflect.Parameter;
import java.util.function.Function;

@Component
@summer.core.annotation.Replaces(summer.runtime.DefaultPageResolver.class)
public class CursorPageableResolver implements HttpParameterResolver {

    @Override
    public boolean supports(Parameter parameter) {
        return CursorPageable.class.isAssignableFrom(parameter.getType());
    }

    @Override
    public Object resolve(HttpContext ctx, Parameter parameter) {
        return buildPageable(ctx);
    }

    @Override
    public Function<HttpContext, Object> compile(Parameter parameter) {
        return this::buildPageable;
    }

    @Override
    public Function<HttpContext, Object> compileAot(Class<?> paramType, String paramName) {
        return this::buildPageable;
    }

    private CursorPageable buildPageable(HttpContext ctx) {
        String cursorParam = ctx.queryParam("cursor");
        Long cursor = null;
        if (cursorParam != null && !cursorParam.isBlank()) {
            cursor = Long.parseLong(cursorParam);
        }
        
        String limitParam = ctx.queryParam("limit");
        int limit = 20;
        if (limitParam != null && !limitParam.isBlank()) {
            limit = Integer.parseInt(limitParam);
        }
        
        return new CursorPageable(cursor, limit);
    }
}
