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
		return ctx.request().pathParam(parameter.getAnnotation(PathParam.class).value());
	}

	@Override
	public Function<HttpContext, Object> compile(Parameter parameter) {
		String paramName = parameter.getAnnotation(PathParam.class).value();
		return ctx -> ctx.request().pathParam(paramName);
	}
}
