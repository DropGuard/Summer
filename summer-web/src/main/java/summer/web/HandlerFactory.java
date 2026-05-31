package summer.web;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import summer.core.Component;
import summer.web.annotation.PathParam;

@Component
public class HandlerFactory {

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
		Class<?> type = param.getType();
		if (type == WebContext.class)
			return ctx;
		if (type == Request.class)
			return ctx.request();
		if (param.isAnnotationPresent(PathParam.class))
			return ctx.request().pathParam(param.getAnnotation(PathParam.class).value());
		if (Throwable.class.isAssignableFrom(type))
			return ctx.request().getAttribute("last_exception");
		return ctx.body(type);
	}
}
