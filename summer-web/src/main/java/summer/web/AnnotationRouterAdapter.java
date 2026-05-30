package summer.web;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.core.Component;
import summer.web.annotation.Delete;
import summer.web.annotation.ExceptionHandler;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;
import summer.web.annotation.Use;
import summer.web.middleware.Middleware;

/**
 * Router adapter that discovers and registers routes and exception handlers
 * from @RestController and @Component annotated classes.
 */
@Component
public class AnnotationRouterAdapter implements RouteRegistrar {
	private static final Logger log = LoggerFactory.getLogger(AnnotationRouterAdapter.class);
	private final Router router;
	private final ApplicationContext context;
	private final ExceptionRegistry exceptionRegistry;
	private final HandlerFactory handlerFactory;

	public AnnotationRouterAdapter(Router router, ApplicationContext context, ExceptionRegistry exceptionRegistry,
			HandlerFactory handlerFactory) {
		this.router = router;
		this.context = context;
		this.exceptionRegistry = exceptionRegistry;
		this.handlerFactory = handlerFactory;
	}

	public void registerControllers() {
		context.getComponentClasses().forEach(this::registerComponent);
	}

	private void registerComponent(Class<?> clazz) {
		Object instance = context.getBean(clazz);

		// 1. Register routes for @RestController
		if (clazz.isAnnotationPresent(RestController.class)) {
			java.util.Arrays.stream(clazz.getMethods())
					.forEach(method -> registerRouteHandler(clazz, instance, method));
		}

		// 2. Register global exception handlers
		java.util.Arrays.stream(clazz.getMethods()).filter(m -> m.isAnnotationPresent(ExceptionHandler.class))
				.forEach(m -> registerExceptionHandler(instance, m));
	}

	private void registerRouteHandler(Class<?> clazz, Object instance, Method method) {
		if (method.isAnnotationPresent(Get.class))
			registerRoute(clazz, instance, method, "GET");
		else if (method.isAnnotationPresent(Post.class))
			registerRoute(clazz, instance, method, "POST");
		else if (method.isAnnotationPresent(Put.class))
			registerRoute(clazz, instance, method, "PUT");
		else if (method.isAnnotationPresent(Delete.class))
			registerRoute(clazz, instance, method, "DELETE");
	}

	private void registerExceptionHandler(Object instance, Method method) {
		ExceptionHandler ann = method.getAnnotation(ExceptionHandler.class);
		Handler handler = handlerFactory.create(instance, method);
		exceptionRegistry.register(ann.value(), handler);
		log.info("Exception Handler registered: {}", ann.value().getSimpleName());
	}

	private void registerRoute(Class<?> clazz, Object instance, Method method, String httpMethod) {
		String path = getRoutePath(clazz, method, httpMethod);
		Handler handler = handlerFactory.create(instance, method);

		// Apply @Use middleware
		List<Class<? extends Middleware>> middlewareClasses = new ArrayList<>();

		// Class-level @Use (Group Middleware)
		if (clazz.isAnnotationPresent(Use.class)) {
			Collections.addAll(middlewareClasses, clazz.getAnnotation(Use.class).value());
		}

		// Method-level @Use
		if (method.isAnnotationPresent(Use.class)) {
			Collections.addAll(middlewareClasses, method.getAnnotation(Use.class).value());
		}

		// Apply in reverse order so the first defined is the outermost
		Collections.reverse(middlewareClasses);
		for (Class<? extends Middleware> mc : middlewareClasses) {
			Middleware m = context.getBean(mc);
			handler = m.apply(handler);
		}

		router.register(httpMethod, path, handler);
		log.info("Route registered (Fast Binding + Middleware): {} {}", httpMethod, path);
	}

	private String getRoutePath(Class<?> clazz, Method method, String httpMethod) {
		// Get path from method annotation
		String methodPath = switch (httpMethod) {
			case "GET" -> method.getAnnotation(Get.class).value();
			case "POST" -> method.getAnnotation(Post.class).value();
			case "PUT" -> method.getAnnotation(Put.class).value();
			case "DELETE" -> method.getAnnotation(Delete.class).value();
			default -> "";
		};

		// Get base path from class annotation
		String basePath = "";
		if (clazz.isAnnotationPresent(RestController.class)) {
			basePath = clazz.getAnnotation(RestController.class).value();
		}

		return combinePaths(basePath, methodPath);
	}

	private String combinePaths(String basePath, String methodPath) {
		if (basePath.isEmpty()) {
			return normalizePath(methodPath);
		}
		if (methodPath.isEmpty()) {
			return normalizePath(basePath);
		}

		String normalizedBase = normalizePath(basePath);
		String normalizedMethod = normalizePath(methodPath);

		if (normalizedBase.endsWith("/") && normalizedMethod.startsWith("/")) {
			return normalizedBase + normalizedMethod.substring(1);
		} else if (!normalizedBase.endsWith("/") && !normalizedMethod.startsWith("/")) {
			return normalizedBase + "/" + normalizedMethod;
		} else {
			return normalizedBase + normalizedMethod;
		}
	}

	private String normalizePath(String path) {
		if (path == null || path.isEmpty()) {
			return "/";
		}
		if (!path.startsWith("/")) {
			return "/" + path;
		}
		return path;
	}
}
