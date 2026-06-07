package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import summer.core.ApplicationContext;
import summer.web.Handler;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.PathUtils;
import summer.web.RouteRegistrar;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

/**
 * Reflection-based route provider that discovers routes from
 * {@code @RestController} annotated classes at runtime.
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link RuntimeWebConfiguration}. Only active when the runtime DI engine is in
 * use (i.e., {@code RuntimeDiMarker} is present).
 * </p>
 */
public class RuntimeRouteRegistrar implements RouteRegistrar {

	private static final Map<Class<? extends Annotation>, HttpMethod> HTTP_METHODS = Map.of(Get.class, HttpMethod.GET,
			Post.class, HttpMethod.POST, Put.class, HttpMethod.PUT, Delete.class, HttpMethod.DELETE);

	private final HttpParameterResolverChain resolverChain;

	public RuntimeRouteRegistrar(HttpParameterResolverChain resolverChain) {
		this.resolverChain = resolverChain;
	}

	@Override
	public void registerControllers(HttpRouter.Builder builder, ApplicationContext context) {
		for (Class<?> clazz : context.getRegisteredTypes()) {
			if (!clazz.isAnnotationPresent(RestController.class))
				continue;
			for (Method method : clazz.getMethods()) {
				registerRoute(builder, context, clazz, method);
			}
		}
	}

	private void registerRoute(HttpRouter.Builder builder, ApplicationContext context, Class<?> clazz, Method method) {
		for (var entry : HTTP_METHODS.entrySet()) {
			Annotation ann = method.getAnnotation(entry.getKey());
			if (ann != null) {
				String path = PathUtils.combinePaths(clazz.getAnnotation(RestController.class).value(),
						annotationValue(ann));
				Handler handler = createHandler(context, clazz, method);
				switch (entry.getValue()) {
					case GET -> builder.get(path, handler);
					case POST -> builder.post(path, handler);
					case PUT -> builder.put(path, handler);
					case DELETE -> builder.delete(path, handler);
				}
				return;
			}
		}
	}

	Handler createHandler(ApplicationContext context, Class<?> clazz, Method method) {
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

	private static String annotationValue(Annotation ann) {
		try {
			return (String) ann.annotationType().getMethod("value").invoke(ann);
		} catch (Exception e) {
			return "";
		}
	}
}
