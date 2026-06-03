package summer.runtime;

import java.lang.reflect.Parameter;

import summer.core.Component;
import summer.web.HttpContext;
import summer.web.Request;
import summer.web.annotation.PathParam;
import summer.web.annotation.QueryParam;
// ParameterResolver is now in the same package

/**
 * Reflection-based parameter resolver for HTTP handler methods.
 *
 * <p>
 * Resolves method parameters by inspecting annotations and types using
 * reflection. This is the default implementation used for runtime web request
 * handling.
 * </p>
 */
@Component
public class ReflectionParameterResolver implements ParameterResolver {

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
			return convertValue(value, type);
		}

		// Exception injection (for @ExceptionHandler)
		if (Throwable.class.isAssignableFrom(type)) {
			return ctx.request().getAttribute("last_exception");
		}

		return null;
	}

	/**
	 * Converts a string value to the target type.
	 */
	private Object convertValue(String value, Class<?> targetType) {
		if (value == null) {
			return defaultValue(targetType);
		}

		if (targetType == String.class) {
			return value;
		} else if (targetType == int.class || targetType == Integer.class) {
			return Integer.parseInt(value);
		} else if (targetType == long.class || targetType == Long.class) {
			return Long.parseLong(value);
		} else if (targetType == boolean.class || targetType == Boolean.class) {
			return Boolean.parseBoolean(value);
		} else if (targetType == double.class || targetType == Double.class) {
			return Double.parseDouble(value);
		} else if (targetType == float.class || targetType == Float.class) {
			return Float.parseFloat(value);
		}

		return value;
	}

	/**
	 * Returns the default value for primitive types.
	 */
	private Object defaultValue(Class<?> targetType) {
		if (targetType == int.class)
			return 0;
		if (targetType == long.class)
			return 0L;
		if (targetType == boolean.class)
			return false;
		if (targetType == double.class)
			return 0.0;
		if (targetType == float.class)
			return 0.0f;
		return null;
	}
}
