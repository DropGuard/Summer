mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.web;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Parameter resolver that handles {@code @Valid}-annotated parameters.
mport com.github.dropguard.summer.core.Internal;
@Internal
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Parses the request body and validates it using the configured {@link
mport com.github.dropguard.summer.core.Internal;
 * com.github.dropguard.summer.validation.BodyValidator}; throws a {@link
mport com.github.dropguard.summer.core.Internal;
 * com.github.dropguard.summer.web.exception.ValidationException} on failure.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public class ValidatingParameterResolver implements HttpParameterResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public boolean supports(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return param.validated();
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public Object resolve(HttpContext ctx, HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        return ctx.validatedBody(param.type());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    @Override
mport com.github.dropguard.summer.core.Internal;
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
mport com.github.dropguard.summer.core.Internal;
        Class<?> type = param.type();
mport com.github.dropguard.summer.core.Internal;
        return ctx -> ctx.validatedBody(type);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
