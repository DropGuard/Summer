package summer.web;

/**
 * Resolves parameters typed as {@link HttpContext} or {@link Request}.
 */
public class TypeParameterResolver implements HttpParameterResolver {

	@Override
	public boolean supports(HandlerParam param) {
		Class<?> type = param.type();
		return type == HttpContext.class || type == Request.class;
	}

	@Override
	public Object resolve(HttpContext ctx, HandlerParam param) {
		return param.type() == HttpContext.class ? ctx : ctx.request();
	}

	@Override
	public java.util.function.Function<HttpContext, Object> compile(HandlerParam param) {
		boolean isCtx = param.type() == HttpContext.class;
		return ctx -> isCtx ? ctx : ctx.request();
	}
}
