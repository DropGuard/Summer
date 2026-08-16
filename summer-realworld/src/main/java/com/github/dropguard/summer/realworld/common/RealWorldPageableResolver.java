package com.github.dropguard.summer.realworld.common;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.Replaces;
import com.github.dropguard.summer.core.data.LimitOffsetPageRequest;
import com.github.dropguard.summer.web.DefaultPageResolver;
import com.github.dropguard.summer.web.HandlerParam;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpParameterResolver;

@Component
@Replaces(DefaultPageResolver.class)
public class RealWorldPageableResolver implements HttpParameterResolver {
    @Override
    public boolean supports(HandlerParam param) {
        return LimitOffsetPageRequest.class.isAssignableFrom(param.type());
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        String limitStr = ctx.queryParam("limit");
        String offsetStr = ctx.queryParam("offset");
        int limit = 20;
        int offset = 0;
        try {
            if (limitStr != null) limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException ignored) {
        }
        try {
            if (offsetStr != null) offset = Integer.parseInt(offsetStr);
        } catch (NumberFormatException ignored) {
        }

        return new LimitOffsetPageRequest(offset, limit);
    }

    @Override
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
        return ctx -> resolve(ctx, param);
    }
}
