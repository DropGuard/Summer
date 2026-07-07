package summer.runtime;

import java.lang.reflect.Parameter;
import java.util.List;
import summer.web.HttpContext;

/**
 * Infrastructure chain that resolves method parameters for HTTP handlers.
 *
 * <p>
 * Manages the built-in parameter resolvers as framework infrastructure, not as
 * user-extensible components. The resolver order is determined by the list
 * passed to the constructor.
 * </p>
 *
 * <p>
 * If no resolver supports the parameter, falls back to
 * {@code ctx.body(param.getType())}.
 * </p>
 */
public final class HttpParameterResolverChain {

	private final List<HttpParameterResolver> resolvers;

	public HttpParameterResolverChain(List<HttpParameterResolver> resolvers) {
		this.resolvers = List.copyOf(resolvers);
	}

	public HttpParameterResolver findResolver(Parameter parameter) {
		for (HttpParameterResolver resolver : resolvers) {
			if (resolver.supports(parameter)) {
				return resolver;
			}
		}
		return null;
	}

	public Object resolve(HttpContext ctx, Parameter parameter) {
		HttpParameterResolver resolver = findResolver(parameter);
		if (resolver != null) {
			return resolver.resolve(ctx, parameter);
		}
		return ctx.body(parameter.getType());
	}
}
