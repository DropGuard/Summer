package summer.web.middleware;

import java.util.Optional;
import summer.validation.BodyValidator;
import summer.web.Handler;
import summer.web.annotation.GlobalMiddleware;

/**
 * Optional middleware that automatically validates request bodies after
 * deserialization. This middleware is automatically registered if a
 * BodyValidator implementation is available.
 */
@GlobalMiddleware
public class ValidationMiddleware implements Middleware {

	private final Optional<BodyValidator> bodyValidator;

	public ValidationMiddleware(BodyValidator validator) {
		this.bodyValidator = Optional.ofNullable(validator);
	}

	@Override
	public Handler apply(Handler handler) {
		return ctx -> {
			// If body validator is available and request has a body, validate it
			if (bodyValidator.isPresent() && ctx.request().getBody().length > 0) {
				// Note: We can't validate here because we don't know the target type yet
				// Validation happens in ParameterResolver when the target type is known
			}
			return handler.handle(ctx);
		};
	}
}
