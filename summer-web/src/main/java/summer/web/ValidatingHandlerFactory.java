package summer.web;

import java.lang.reflect.Parameter;
import jakarta.validation.Valid;
import summer.core.Component;
import summer.core.annotation.Replaces;

/**
 * Handler factory that adds @Valid support via reflection.
 * Extends HandlerFactory to override resolveArg() and add validation logic.
 * Replaces the base HandlerFactory in the DI container.
 */
@Component
@Replaces(HandlerFactory.class)
public class ValidatingHandlerFactory extends HandlerFactory {

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
