package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;

/**
 * Parameter resolver that handles {@code @Valid}-annotated parameters.
 *
 * <p>Parses the request body and validates it via the framework's body-validation contract ({@code
 * ctx.validatedBody(...)}); throws a {@link
 * com.github.dropguard.summer.web.exception.ValidationException} on failure.
 */
@Internal
public class ValidatingParameterResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return param.validated();
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        return ctx.validatedBody(param.type());
    }
}
