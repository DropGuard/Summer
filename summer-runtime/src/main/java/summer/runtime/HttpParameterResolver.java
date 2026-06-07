package summer.runtime;

import java.lang.reflect.Parameter;
import summer.web.HttpContext;

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
}
