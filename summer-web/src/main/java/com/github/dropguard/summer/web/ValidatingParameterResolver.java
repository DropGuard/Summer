package com.github.dropguard.summer.web;

/**
 * Parameter resolver that handles {@code @Valid}-annotated parameters.
 *
 * <p>Parses the request body and validates it using the configured {@link
 * com.github.dropguard.summer.validation.BodyValidator}; throws a {@link
 * com.github.dropguard.summer.web.exception.ValidationException} on failure.
 */
public class ValidatingParameterResolver implements HttpParameterResolver {

    @Override
    public boolean supports(HandlerParam param) {
        return param.validated();
    }

    @Override
    public Object resolve(HttpContext ctx, HandlerParam param) {
        return ctx.validatedBody(param.type());
    }

    @Override
    public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
        Class<?> type = param.type();
        return ctx -> ctx.validatedBody(type);
    }
}
