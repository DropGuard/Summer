package summer.runtime;

import java.lang.reflect.Parameter;
import java.util.function.Function;
import summer.core.config.TypeConverter;
import summer.web.HttpContext;
import summer.web.annotation.QueryParam;

/**
 * Resolves {@link QueryParam @QueryParam}-annotated parameters from the URL
 * query string. Uses {@link TypeConverter} for type conversion.
 */
public class QueryParamResolver implements HttpParameterResolver {

	@Override
	public boolean supports(Parameter parameter) {
		return parameter.isAnnotationPresent(QueryParam.class);
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		String value = ctx.request().queryParam(parameter.getAnnotation(QueryParam.class).value());
		return TypeConverter.convert(value, parameter.getType());
	}

	@Override
	public Function<HttpContext, Object> compile(Parameter parameter) {
		String paramName = parameter.getAnnotation(QueryParam.class).value();
		Class<?> type = parameter.getType();
		return ctx -> TypeConverter.convert(ctx.request().queryParam(paramName), type);
	}
}
