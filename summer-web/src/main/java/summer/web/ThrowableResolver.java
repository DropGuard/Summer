package summer.web;

/**
 * Resolves {@link Throwable}-typed parameters for {@code @ExceptionHandler}
 * methods. Reads the "last_exception" request attribute set by the framework.
 */
public class ThrowableResolver implements HttpParameterResolver {

	@Override
	public boolean supports(HandlerParam param) {
		return Throwable.class.isAssignableFrom(param.type());
	}

	@Override
	public Object resolve(HttpContext ctx, HandlerParam param) {
		return ctx.request().getAttribute(RequestAttributes.LAST_EXCEPTION);
	}

	@Override
	public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
		return ctx -> ctx.request().getAttribute(RequestAttributes.LAST_EXCEPTION);
	}
}
