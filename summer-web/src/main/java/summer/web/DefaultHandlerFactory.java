package summer.web;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import summer.core.Component;
import summer.web.resolver.ParameterResolver;

/**
 * Default handler factory using parameter resolvers to bind request data.
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
public class DefaultHandlerFactory implements HandlerFactory {

	private final List<ParameterResolver> resolvers;

	public DefaultHandlerFactory(List<ParameterResolver> resolvers) {
		this.resolvers = resolvers;
	}

	@Override
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
