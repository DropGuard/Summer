package summer.web;

import jakarta.validation.Valid;
import java.lang.reflect.Parameter;
import java.util.List;
import summer.core.Component;
import summer.core.annotation.Replaces;
import summer.web.resolver.ParameterResolver;

/**
 * Handler factory that adds @Valid support via reflection. Extends
 * HandlerFactory to override resolveArg() and add validation logic. Replaces
 * the base HandlerFactory in the DI container.
 */
@Component
@Replaces(HandlerFactory.class)
public class ValidatingHandlerFactory extends HandlerFactory {

	public ValidatingHandlerFactory(List<ParameterResolver> resolvers) {
		super(resolvers);
	}

	@Override
	protected Object resolveArg(WebContext ctx, Parameter param) {
		// Use validatedBody for @Valid parameters
		if (param.isAnnotationPresent(Valid.class)) {
			return ctx.validatedBody(param.getType());
		}

		// Delegate to parent for all other parameters
		return super.resolveArg(ctx, param);
	}
}
