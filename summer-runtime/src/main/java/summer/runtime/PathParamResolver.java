package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;
import java.util.function.Function;
import summer.web.annotation.PathParam;

/**
 * Resolves {@link PathParam @PathParam}-annotated parameters from URL path
 * segments.
 */
public class PathParamResolver implements HttpParameterResolver {

	@Override
	public boolean supports(Parameter parameter) {
		return parameter.isAnnotationPresent(PathParam.class);
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		String raw = ctx.request().pathParam(parameter.getAnnotation(PathParam.class).value());
		return convert(raw, parameter.getType());
	}

	@Override
	public Function<HttpContext, Object> compile(Parameter parameter) {
		String paramName = parameter.getAnnotation(PathParam.class).value();
		Class<?> targetType = parameter.getType();
		return ctx -> convert(ctx.request().pathParam(paramName), targetType);
	}

	private static Object convert(String raw, Class<?> targetType) {
		if (raw == null) {
			return null;
		}
		if (targetType == String.class) {
			return raw;
		}
		if (targetType == Long.class || targetType == long.class) {
			return Long.valueOf(raw);
		}
		if (targetType == Integer.class || targetType == int.class) {
			return Integer.valueOf(raw);
		}
		if (targetType == Boolean.class || targetType == boolean.class) {
			return Boolean.valueOf(raw);
		}
		if (targetType == Double.class || targetType == double.class) {
			return Double.valueOf(raw);
		}
		return raw;
	}
}
