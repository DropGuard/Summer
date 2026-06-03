package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import summer.core.ApplicationContext;
import summer.core.Component;
import summer.web.ExceptionRegistry;
import summer.web.Handler;
import summer.web.HttpRouter;
import summer.web.PathUtils;
import summer.web.RouteRegistrar;
import summer.web.annotation.Delete;
import summer.web.annotation.ExceptionHandler;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;
import summer.web.annotation.Use;
import summer.web.middleware.Middleware;

/**
 * Reflection-based route registrar that discovers routes from
 * {@code @RestController} annotated classes at runtime.
 */
@Component
public class AnnotationRouterAdapter implements RouteRegistrar {

	private static final Map<Class<? extends Annotation>, String> HTTP_METHODS = Map.of(
			Get.class, "GET", Post.class, "POST", Put.class, "PUT", Delete.class, "DELETE");

	private final HttpRouter router;
	private final ApplicationContext context;
	private final ExceptionRegistry exceptionRegistry;
	private final List<ParameterResolver> resolvers;

	public AnnotationRouterAdapter(HttpRouter router, ApplicationContext context, ExceptionRegistry exceptionRegistry,
			List<ParameterResolver> resolvers) {
		this.router = router;
		this.context = context;
		this.exceptionRegistry = exceptionRegistry;
		this.resolvers = resolvers;
	}

	@Override
	public void registerControllers() {
		for (Class<?> clazz : context.getRegisteredTypes()) {
			for (Method method : clazz.getMethods()) {
				if (clazz.isAnnotationPresent(RestController.class))
					registerRoute(clazz, method);
				registerExceptionHandler(clazz, method);
			}
		}
	}

	private void registerRoute(Class<?> clazz, Method method) {
		for (var entry : HTTP_METHODS.entrySet()) {
			Annotation ann = method.getAnnotation(entry.getKey());
			if (ann != null) {
				String path = PathUtils.combinePaths(
						clazz.getAnnotation(RestController.class).value(),
						annotationValue(ann));
				Handler handler = applyMiddleware(clazz, method, createHandler(clazz, method));
				router.register(entry.getValue(), path, handler);
				return;
			}
		}
	}

	private void registerExceptionHandler(Class<?> clazz, Method method) {
		ExceptionHandler ann = method.getAnnotation(ExceptionHandler.class);
		if (ann != null) {
			exceptionRegistry.register(ann.value(), createHandler(clazz, method));
		}
	}

	private Handler createHandler(Class<?> clazz, Method method) {
		method.setAccessible(true);
		Object instance = context.getBean(clazz);
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
				throw (cause instanceof RuntimeException re) ? re
						: new summer.aop.SummerAopException("Handler invocation failed", cause);
			} catch (IllegalAccessException e) {
				throw new summer.aop.SummerAopException("Cannot access handler method", e);
			}
		};
	}

	private Object resolveArg(summer.web.HttpContext ctx, Parameter param) {
		for (ParameterResolver resolver : resolvers) {
			if (resolver.supports(param)) {
				return resolver.resolve(ctx, param);
			}
		}
		return ctx.body(param.getType());
	}

	private Handler applyMiddleware(Class<?> clazz, Method method, Handler handler) {
		List<Class<? extends Middleware>> middlewares = new ArrayList<>();
		if (clazz.isAnnotationPresent(Use.class))
			Collections.addAll(middlewares, clazz.getAnnotation(Use.class).value());
		if (method.isAnnotationPresent(Use.class))
			Collections.addAll(middlewares, method.getAnnotation(Use.class).value());

		Collections.reverse(middlewares);
		for (Class<? extends Middleware> mc : middlewares)
			handler = context.getBean(mc).apply(handler);
		return handler;
	}

	private static String annotationValue(Annotation ann) {
		try {
			return (String) ann.annotationType().getMethod("value").invoke(ann);
		} catch (Exception e) {
			return "";
		}
	}
}
