package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;
import java.util.function.Function;

/**
 * Resolves method parameters for HTTP request handlers.
 *
 * <p>
 * Implementations of this interface are responsible for binding request data to
 * method parameters. Different implementations can be provided for different DI
 * engines:
 * </p>
 * <ul>
 * <li>Runtime: reflection-based resolution</li>
 * <li>AOT: compile-time code generation (zero reflection)</li>
 * </ul>
 */
public interface HttpParameterResolver {

	/**
	 * Checks if this resolver can handle the given parameter.
	 *
	 * @param parameter
	 *            the method parameter
	 * @return true if this resolver can resolve the parameter
	 */
	boolean supports(Parameter parameter);

	/**
	 * Resolves the parameter value from the web context.
	 *
	 * @param ctx
	 *            the web context
	 * @param parameter
	 *            the method parameter
	 * @return the resolved value
	 */
	Object resolve(HttpContext ctx, Parameter parameter);

	/**
	 * Compiles the parameter resolution logic into a fast execution plan during cold start.
	 * This prevents per-request reflection overhead.
	 *
	 * @param parameter the method parameter to compile
	 * @return a pre-compiled execution function
	 */
	default Function<HttpContext, Object> compile(Parameter parameter) {
		return ctx -> resolve(ctx, parameter);
	}

	default Function<HttpContext, Object> compileAot(Class<?> paramType, String paramName) {
		throw new UnsupportedOperationException("AOT not supported by this resolver");
	}
}
