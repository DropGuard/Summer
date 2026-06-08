package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;
import summer.web.Request;
import summer.web.annotation.PathParam;
import summer.web.annotation.QueryParam;

/**
 * Reflection-based parameter resolver for HTTP handler methods.
 *
 * <p>
 * Resolves method parameters by inspecting annotations and types using
 * reflection. This is a built-in infrastructure component.
 * </p>
 */
public class ReflectionParameterResolver implements HttpParameterResolver {

	@Override
	public boolean supports(Parameter parameter) {
		Class<?> type = parameter.getType();
		return type == HttpContext.class || type == Request.class || parameter.isAnnotationPresent(PathParam.class)
				|| parameter.isAnnotationPresent(QueryParam.class) || Throwable.class.isAssignableFrom(type);
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		Class<?> type = parameter.getType();

		// WebContext injection
		if (type == HttpContext.class) {
			return ctx;
		}

		// Request injection
		if (type == Request.class) {
			return ctx.request();
		}

		// @PathParam binding
		if (parameter.isAnnotationPresent(PathParam.class)) {
			return ctx.request().pathParam(parameter.getAnnotation(PathParam.class).value());
		}

		// @QueryParam binding
		if (parameter.isAnnotationPresent(QueryParam.class)) {
			String value = ctx.request().queryParam(parameter.getAnnotation(QueryParam.class).value());
			return TypeConverter.convert(value, type);
		}

		// Exception injection (for @ExceptionHandler)
		if (Throwable.class.isAssignableFrom(type)) {
			return ctx.request().getAttribute("last_exception");
		}

		return null;
	}

}
