package summer.web;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Parameter;

/**
 * Interface for resolving controller method arguments from a WebContext.
 * Resolvers provide a MethodHandle that performs the extraction during startup.
 */
public interface ArgumentResolver {
	/**
	 * Checks if this resolver can handle the given parameter.
	 */
	boolean supports(Parameter parameter);

	/**
	 * Returns a MethodHandle that maps (WebContext) -> TargetType.
	 */
	MethodHandle resolve(Parameter parameter, MethodHandles.Lookup lookup) throws Exception;
}
