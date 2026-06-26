package summer.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.jboss.jandex.MethodInfo;
import summer.web.Handler;

/**
 * Creates {@link Handler}s from controller or exception-handler methods.
 */
public final class HandlerFactory {

	private HandlerFactory() {
	}

	/**
	 * Creates a Handler from a Java reflection method.
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
				method.invoke(instance, args);
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

	/**
	 * Creates a Handler from a Jandex {@link MethodInfo}. Resolves the Java
	 * reflection method at build time from the instance class.
	 */
	public static Handler create(Object instance, MethodInfo methodInfo, HttpParameterResolverChain resolverChain) {
		try {
			Method method = instance.getClass().getMethod(methodInfo.name());
			return create(instance, method, resolverChain);
		} catch (NoSuchMethodException e) {
			throw new summer.aop.SummerAopException("Method not found: " + methodInfo.name(), e);
		}
	}
}
