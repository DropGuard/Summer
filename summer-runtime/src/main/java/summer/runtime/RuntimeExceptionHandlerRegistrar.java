package summer.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import summer.core.ApplicationContext;
import summer.web.ExceptionHandlerRegistrar;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.annotation.ExceptionHandler;

/**
 * Reflection-based exception handler registrar that discovers
 * {@code @ExceptionHandler} methods at runtime.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link RuntimeWebConfiguration}. Only active when the runtime DI engine is in
 * use (i.e., {@code RuntimeDiMarker} is present).
 * </p>
 */
public class RuntimeExceptionHandlerRegistrar implements ExceptionHandlerRegistrar {

	private final HttpParameterResolverChain resolverChain;

	public RuntimeExceptionHandlerRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerHandlers(ExceptionRegistry registry, ApplicationContext context) {
		for (Class<?> clazz : context.getRegisteredTypes()) {
			for (Method method : clazz.getMethods()) {
				ExceptionHandler ann = method.getAnnotation(ExceptionHandler.class);
				if (ann != null) {
					Handler handler = createHandler(context, clazz, method);
					registry.register(ann.value(), handler);
				}
			}
		}
	}

	private Handler createHandler(ApplicationContext context, Class<?> clazz, Method method) {
		method.setAccessible(true);
		Parameter[] params = method.getParameters();
		Object instance = context.getBean(clazz);
		return ctx -> {
			Object[] args = new Object[params.length];
			for (int i = 0; i < params.length; i++) {
				args[i] = resolverChain.resolve(ctx, params[i]);
			}
			try {
				return method.invoke(instance, args);
			} catch (java.lang.reflect.InvocationTargetException e) {
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
