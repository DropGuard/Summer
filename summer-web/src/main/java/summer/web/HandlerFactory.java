package summer.web;

import java.lang.reflect.Method;

/**
 * Interface for creating HTTP request handlers.
 *
 * <p>
 * Implementations create handlers that bind request data to method parameters.
 * Different implementations can provide different binding strategies (e.g.,
 * reflection-based, AOT-generated).
 * </p>
 */
public interface HandlerFactory {

	/**
	 * Creates a handler for the given method.
	 *
	 * @param instance the object instance containing the method
	 * @param method   the method to invoke
	 * @return a handler that invokes the method with resolved parameters
	 */
	Handler create(Object instance, Method method);
}
