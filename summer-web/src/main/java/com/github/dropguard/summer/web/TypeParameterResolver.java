package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;

/**
 * Resolves parameters typed as {@link HttpContext}, {@link Request}, {@link SseStream}, or {@link
 * ChunkedResponse}.
 */
@Internal
public class TypeParameterResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        Class<?> type = param.type();
        return type == HttpContext.class
                || type == Request.class
                || type == SseStream.class
                || type == ChunkedResponse.class;
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        Class<?> type = param.type();
        if (type == HttpContext.class) {
            return ctx;
        }
        if (type == Request.class) {
            return ctx.request();
        }
        if (type == SseStream.class) {
            ctx.setHandled(true);
            return ctx.request().getAttribute(RequestAttributes.SSE_STREAM);
        }
        if (type == ChunkedResponse.class) {
            ctx.setHandled(true);
            return ctx.request().getAttribute(RequestAttributes.CHUNKED_RESPONSE);
        }
        return null;
    }
}
