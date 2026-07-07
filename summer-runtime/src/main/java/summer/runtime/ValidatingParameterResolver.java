package summer.runtime;

import jakarta.validation.Valid;
import java.lang.reflect.Parameter;
import java.util.function.Function;
import summer.web.HttpContext;

/**
 * Parameter resolver that handles {@code @Valid} annotated parameters.
 *
 * <p>
 * When a method parameter is annotated with {@code @Valid}, this resolver
 * parses the request body and validates it using the configured
 * {@link summer.validation.BodyValidator}. If validation fails, a
 * {@link summer.web.exception.ValidationException} is thrown.
 * </p>
 *
 * <p>
 * This resolver is part of the {@link HttpParameterResolver} chain and is
 * consulted before the fallback body binding in
 * {@link AnnotationRouterAdapter}.
 * </p>
 */
public class ValidatingParameterResolver implements HttpParameterResolver {

	@Override
	public boolean supports(Parameter parameter) {
		return parameter.isAnnotationPresent(Valid.class);
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		return ctx.validatedBody(parameter.getType());
	}

	@Override
	public Function<HttpContext, Object> compile(Parameter parameter) {
		Class<?> type = parameter.getType();
		return ctx -> ctx.validatedBody(type);
	}
}
