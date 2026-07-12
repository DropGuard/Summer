package summer.runtime;

import java.lang.reflect.Parameter;
import java.util.function.Function;
import summer.core.config.PageableProperties;
import summer.web.HttpContext;

public class DefaultPageResolver implements HttpParameterResolver {

	private final int defaultPage;
	private final int defaultSize;

	public DefaultPageResolver(PageableProperties props) {
		this.defaultPage = props.defaultPage();
		this.defaultSize = props.defaultSize();
	}

	@Override
	public boolean supports(Parameter parameter) {
		return DefaultPageRequest.class.isAssignableFrom(parameter.getType());
	}

	@Override
	public Object resolve(HttpContext ctx, Parameter parameter) {
		int page = parseQueryParam(ctx, "page", defaultPage);
		int size = parseQueryParam(ctx, "size", defaultSize);

		return new DefaultPageRequest(page, size);
	}

	@Override
	public Function<HttpContext, Object> compile(Parameter parameter) {
		return ctx -> resolve(ctx, parameter);
	}

	@Override
	public Function<HttpContext, Object> compileAot(Class<?> paramType, String paramName) {
		return ctx -> resolve(ctx, null);
	}

	private int parseQueryParam(HttpContext ctx, String name, int defaultValue) {
		String value = ctx.queryParam(name);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			int parsed = Integer.parseInt(value);
			return Math.max(0, parsed);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
