package summer.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import summer.web.Handler;

/**
 * Creates {@link Handler}s from controller or exception-handler methods.
 *
 * <p>
 * Eliminates the duplicated {@code createHandler()} logic previously found in
 * both {@code RuntimeRouteRegistrar} and
 * {@code RuntimeExceptionHandlerRegistrar}.
 * </p>
 */
public final class HandlerFactory {

	private HandlerFactory() {
	}

	/**
	 * Creates a Handler that resolves all method parameters via the given chain,
	 * then invokes the method reflectively.
	 *
	 * @param instance
	 *            the controller or handler bean instance
	 * @param method
	 *            the method to invoke
	 * @param resolverChain
	 *            the parameter resolver chain
	 * @return a Handler that dispatches to the method
	 */
	public static Handler create(Object instance, Method method, HttpParameterResolverChain resolverChain) {
		method.setAccessible(true);
		Parameter[] params = method.getParameters();
		return ctx -> {
			Object[] args = new Object[params.length];
			for (int i = 0; i < params.length; i++) {
				args[i] = resolverChain.resolve(ctx, params[i]);
			}
			try {
				return method.invoke(instance, args);
			} catch (InvocationTargetException e) {
				Throwable cause = e.getTargetException();
				throw (cause instanceof RuntimeException re)
						? re
						: new summer.aop.SummerAopException("Handler invocation failed", cause);
			} catch (IllegalAccessException e) {
				throw new summer.aop.SummerAopException("Cannot access handler method", e);
			}
		};
	}
}
