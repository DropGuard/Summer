package summer.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Function;
import org.jboss.jandex.MethodInfo;
import summer.web.Handler;
import summer.web.HttpContext;

/**
 * Creates {@link Handler}s from controller or exception-handler methods.
 */
public final class HandlerFactory {

	private HandlerFactory() {
	}

	/**
	 * Creates a Handler from a Java reflection method.
	 */
	@SuppressWarnings("unchecked")
	public static Handler create(Object instance, Method method, HttpParameterResolverChain resolverChain) {
		method.setAccessible(true);
		Parameter[] params = method.getParameters();
		
		// Cold-start parsing: Pre-resolve the parameter providers once
		Function<HttpContext, Object>[] paramProviders = new Function[params.length];
		
		for (int i = 0; i < params.length; i++) {
			Parameter param = params[i];
			HttpParameterResolver resolvedResolver = resolverChain.findResolver(param);
			if (resolvedResolver != null) {
				paramProviders[i] = resolvedResolver.compile(param);
			} else {
				paramProviders[i] = ctx -> ctx.body(param.getType());
			}
		}

		return ctx -> {
			Object[] args = new Object[params.length];
			for (int i = 0; i < params.length; i++) {
				args[i] = paramProviders[i].apply(ctx);
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
