package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;
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
}
