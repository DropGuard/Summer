package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;
import summer.web.Request;

/**
 * Resolves parameters typed as {@link HttpContext} or {@link Request}.
 */
public class TypeParameterResolver implements HttpParameterResolver {

	@Override
	public boolean supports(Parameter parameter) {
		Class<?> type = parameter.getType();
		return type == HttpContext.class || type == Request.class;
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		return parameter.getType() == HttpContext.class ? ctx : ctx.request();
	}
}
