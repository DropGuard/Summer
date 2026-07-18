package summer.realworld.common;

import summer.core.Component;
import summer.core.annotation.Replaces;
import summer.web.DefaultPageResolver;
import summer.web.HandlerParam;
import summer.web.HttpContext;
import summer.web.HttpParameterResolver;

@Component
@Replaces(DefaultPageResolver.class)
public class RealWorldPageableResolver implements HttpParameterResolver {
    @Override
    public boolean supports(HandlerParam param) {
        return LimitOffsetPageable.class.isAssignableFrom(param.type());
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
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
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
        return ctx -> resolve(ctx, param);
    }
}
