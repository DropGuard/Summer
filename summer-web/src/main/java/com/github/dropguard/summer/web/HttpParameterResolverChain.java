mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
@Internal
 * Infrastructure chain that resolves method parameters for HTTP handlers.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Manages the built-in parameter resolvers as framework infrastructure, ordered by the list
mport com.github.dropguard.summer.core.Internal;
 * passed to the constructor. If no resolver supports a parameter, the chain falls back to {@code
mport com.github.dropguard.summer.core.Internal;
 * ctx.body(param.type())}.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class HttpParameterResolverChain {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final List<HttpParameterResolver> resolvers;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public HttpParameterResolverChain(List<HttpParameterResolver> resolvers) {
mport com.github.dropguard.summer.core.Internal;
        this.resolvers = List.copyOf(resolvers);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public HttpParameterResolver findResolver(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        for (HttpParameterResolver resolver : resolvers) {
mport com.github.dropguard.summer.core.Internal;
            if (resolver.supports(param)) {
mport com.github.dropguard.summer.core.Internal;
                return resolver;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        HttpParameterResolver resolver = findResolver(param);
mport com.github.dropguard.summer.core.Internal;
        if (resolver != null) {
mport com.github.dropguard.summer.core.Internal;
            return resolver.resolve(ctx, param);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return ctx.body(param.type());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
