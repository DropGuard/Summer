package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;
import summer.web.RequestAttributes;

/**
 * Resolves {@link Throwable}-typed parameters for {@code @ExceptionHandler}
 * methods. Reads the "last_exception" request attribute set by the framework.
 */
public class ThrowableResolver implements HttpParameterResolver {

	@Override
	public boolean supports(Parameter parameter) {
		return Throwable.class.isAssignableFrom(parameter.getType());
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		return ctx.request().getAttribute(RequestAttributes.LAST_EXCEPTION);
	}
}
