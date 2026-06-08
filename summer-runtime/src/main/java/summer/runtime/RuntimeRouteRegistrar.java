package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
				Handler handler = HandlerFactory.create(context.getBean(clazz), method, resolverChain);
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

	private static String annotationValue(Annotation ann) {
		try {
			return (String) ann.annotationType().getMethod("value").invoke(ann);
		} catch (Exception e) {
			return "";
		}
	}
}
