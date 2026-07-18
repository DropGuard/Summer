package summer.twitter.common;

import summer.core.Component;
import summer.core.annotation.Replaces;
import summer.web.DefaultPageResolver;
import summer.web.HandlerParam;
import summer.web.HttpContext;
import summer.web.HttpParameterResolver;

@Component
@Replaces(DefaultPageResolver.class)
public class CursorPageableResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return CursorPageable.class.isAssignableFrom(param.type());
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        return buildPageable(ctx);
    }

    @Override
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
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
