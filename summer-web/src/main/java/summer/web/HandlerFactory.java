package summer.web;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import summer.core.Component;
import summer.web.resolver.ParameterResolver;

/**
 * Factory for creating HTTP request handlers.
 *
 * <p>
 * Uses {@link ParameterResolver} to bind request data to method parameters.
 * The resolver is injected at construction time, allowing different
 * implementations for different DI engines:
 * </p>
 * <ul>
 * <li>Runtime: {@code ReflectionParameterResolver} (reflection-based)</li>
 * <li>AOT: generated code (zero reflection)</li>
 * </ul>
 */
@Component
public class HandlerFactory {

	private final List<ParameterResolver> resolvers;

	public HandlerFactory(List<ParameterResolver> resolvers) {
		this.resolvers = resolvers;
	}

	public Handler create(Object instance, Method method) {
		method.setAccessible(true);
		Parameter[] params = method.getParameters();
		return ctx -> {
			Object[] args = new Object[params.length];
			for (int i = 0; i < params.length; i++) {
				args[i] = resolveArg(ctx, params[i]);
			}
			try {
				return method.invoke(instance, args);
			} catch (java.lang.reflect.InvocationTargetException e) {
				Throwable cause = e.getTargetException();
				throw (cause instanceof RuntimeException re) ? re : new RuntimeException(cause);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		};
	}

	protected Object resolveArg(WebContext ctx, Parameter param) {
		for (ParameterResolver resolver : resolvers) {
			if (resolver.supports(param)) {
				return resolver.resolve(ctx, param);
			}
		}

		// Default: try to bind from request body
		return ctx.body(param.getType());
	}
}
