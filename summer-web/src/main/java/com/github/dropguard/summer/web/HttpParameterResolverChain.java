package com.github.dropguard.summer.web;

import java.util.List;

/**
 * Infrastructure chain that resolves method parameters for HTTP handlers.
 *
 * <p>Manages the built-in parameter resolvers as framework infrastructure, ordered by the list
 * passed to the constructor. If no resolver supports a parameter, the chain falls back to {@code
 * ctx.body(param.type())}.
 */
public final class HttpParameterResolverChain {

    private final List<HttpParameterResolver> resolvers;

    public HttpParameterResolverChain(List<HttpParameterResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    public HttpParameterResolver findResolver(HandlerParam param) {
        for (HttpParameterResolver resolver : resolvers) {
            if (resolver.supports(param)) {
                return resolver;
            }
        }
        return null;
    }

    public Object resolve(HttpContext ctx, HandlerParam param) {
        HttpParameterResolver resolver = findResolver(param);
        if (resolver != null) {
            return resolver.resolve(ctx, param);
        }
        return ctx.body(param.type());
    }
}
