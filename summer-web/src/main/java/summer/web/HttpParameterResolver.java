package summer.web;

import java.util.function.Function;

/**
 * Resolves method parameters for HTTP request handlers.
 *
 * <p>
 * Implementations bind request data to a handler parameter based on its
 * {@link HandlerParam#binding() binding intent}. Resolution is engine-agnostic:
 * the runtime engine drives resolvers through its reflective discovery chain,
 * while the AOT engine inlines equivalent logic at code-generation time — both
 * read from the same {@link HandlerParam} description and must behave
 * identically.
 * </p>
 *
 * <p>
 * This is web-layer infrastructure, owned by {@code summer-web}, not by either
 * DI engine. The runtime engine assembles the resolver chain; the AOT engine
 * does not invoke resolvers at all (it generates inline handlers).
 * </p>
 */
public interface HttpParameterResolver {

	/**
	 * Checks if this resolver can handle the given parameter.
	 *
	 * @param param
	 *            the reflection-free parameter description
	 * @return true if this resolver can resolve the parameter
	 */
	boolean supports(HandlerParam param);

	/**
	 * Resolves the parameter value from the web context.
	 *
	 * @param ctx
	 *            the web context
	 * @param param
	 *            the reflection-free parameter description
	 * @return the resolved value
	 */
	Object resolve(HttpContext ctx, HandlerParam param);

	/**
	 * Compiles the parameter resolution logic into a fast execution plan. Default
	 * delegates to {@link #resolve(HttpContext, HandlerParam)} wrapped as a
	 * function.
	 *
	 * @param param
	 *            the reflection-free parameter description
	 * @return a pre-compiled execution function
	 */
	default Function<HttpContext, Object> compile(HandlerParam param) {
		return ctx -> resolve(ctx, param);
	}
}
