package summer.web;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import summer.core.ApplicationContext;
import summer.core.Component;
import summer.web.annotation.Delete;
import summer.web.annotation.ExceptionHandler;
import summer.web.annotation.Get;
import summer.web.annotation.PathParam;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;
import summer.web.annotation.Use;
import summer.web.middleware.Middleware;
import summer.web.resolver.BodyResolver;
import summer.web.resolver.ExceptionResolver;
import summer.web.resolver.PathParamResolver;
import summer.web.resolver.RequestResolver;
import summer.web.resolver.ResponseResolver;
import summer.web.resolver.WebContextResolver;

/**
 * Router adapter that discovers and registers routes and exception handlers 
 * from @RestController and @Component annotated classes.
 */
@Component
public class AnnotationRouterAdapter {
	private final Router router;
	private final ApplicationContext context;
	private final ExceptionRegistry exceptionRegistry;
	
	private final List<ArgumentResolver> resolvers = List.of(
		new WebContextResolver(),
		new RequestResolver(),
		new ResponseResolver(),
		new PathParamResolver(),
		new ExceptionResolver(),
		new BodyResolver() // Fallback
	);

	public AnnotationRouterAdapter(Router router, ApplicationContext context, ExceptionRegistry exceptionRegistry) {
		this.router = router;
		this.context = context;
		this.exceptionRegistry = exceptionRegistry;
	}

	public void registerControllers() {
		context.getComponentClasses().forEach(this::registerComponent);
	}

	private void registerComponent(Class<?> clazz) {
		Object instance = context.getBean(clazz);

		// 1. Register routes for @RestController
		if (clazz.isAnnotationPresent(RestController.class)) {
			java.util.Arrays.stream(clazz.getMethods()).forEach(method -> registerRouteHandler(clazz, instance, method));
		}

		// 2. Register global exception handlers
		java.util.Arrays.stream(clazz.getMethods())
				.filter(m -> m.isAnnotationPresent(ExceptionHandler.class))
				.forEach(m -> registerExceptionHandler(instance, m));
	}

	private void registerRouteHandler(Class<?> clazz, Object instance, Method method) {
		if (method.isAnnotationPresent(Get.class)) registerRoute(clazz, instance, method, "GET");
		else if (method.isAnnotationPresent(Post.class)) registerRoute(clazz, instance, method, "POST");
		else if (method.isAnnotationPresent(Put.class)) registerRoute(clazz, instance, method, "PUT");
		else if (method.isAnnotationPresent(Delete.class)) registerRoute(clazz, instance, method, "DELETE");
	}

	private void registerExceptionHandler(Object instance, Method method) {
		ExceptionHandler ann = method.getAnnotation(ExceptionHandler.class);
		Handler handler = createFastHandler(instance, method);
		exceptionRegistry.register(ann.value(), handler);
		System.out.println("Exception Handler registered: " + ann.value().getSimpleName());
	}

	private void registerRoute(Class<?> clazz, Object instance, Method method, String httpMethod) {
		String path = getRoutePath(clazz, method, httpMethod);
		Handler handler = createFastHandler(instance, method);
		
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
		System.out.println("Route registered (Fast Binding + Middleware): " + httpMethod + " " + path);
	}

	private Handler createFastHandler(Object instance, Method method) {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			MethodHandle methodHandle = lookup.unreflect(method).bindTo(instance);
			MethodHandle adaptedHandle = methodHandle;

			// 1. Handle Return Value (void -> "")
			if (method.getReturnType().equals(void.class)) {
				MethodHandle constantEmpty = MethodHandles.constant(Object.class, "");
				adaptedHandle = MethodHandles.filterReturnValue(methodHandle, MethodHandles.dropArguments(constantEmpty, 0, void.class));
			} else {
				adaptedHandle = adaptedHandle.asType(methodHandle.type().changeReturnType(Object.class));
			}

			// 2. Build Argument Extractors (WebContext -> Parameter)
			Parameter[] parameters = method.getParameters();
			MethodHandle[] filters = new MethodHandle[parameters.length];
			int[] reorder = new int[parameters.length];

			for (int i = 0; i < parameters.length; i++) {
				Parameter p = parameters[i];
				reorder[i] = 0; // All filters consume the same WebContext (index 0)
				
				for (ArgumentResolver resolver : resolvers) {
					if (resolver.supports(p)) {
						filters[i] = resolver.resolve(p, lookup);
						break;
					}
				}
			}

			adaptedHandle = MethodHandles.filterArguments(adaptedHandle, 0, filters);
			adaptedHandle = MethodHandles.permuteArguments(adaptedHandle, MethodType.methodType(Object.class, WebContext.class), reorder);

			CallSite site = LambdaMetafactory.metafactory(
					lookup, "handle", MethodType.methodType(Handler.class),
					MethodType.methodType(Object.class, WebContext.class),
					adaptedHandle, MethodType.methodType(Object.class, WebContext.class)
			);

			return (Handler) site.getTarget().invoke();
		} catch (Throwable t) {
			// Fallback (e.g. for ExceptionHandler where we need to pass the exception object)
			return ctx -> {
				Object[] args = new Object[method.getParameterCount()];
				Parameter[] params = method.getParameters();
				for (int i = 0; i < params.length; i++) {
					Class<?> type = params[i].getType();
					if (type.equals(WebContext.class)) args[i] = ctx;
					else if (type.equals(Request.class)) args[i] = ctx.request();
					else if (type.equals(Response.class)) args[i] = ctx.response();
					else if (params[i].isAnnotationPresent(PathParam.class)) args[i] = ctx.request().pathParam(params[i].getAnnotation(PathParam.class).value());
					else if (Throwable.class.isAssignableFrom(type)) args[i] = ctx.request().getAttribute("last_exception");
					else args[i] = ctx.body(type);
				}
				try {
					Object res = method.invoke(instance, args);
					return method.getReturnType().equals(void.class) ? "" : res;
				} catch (Exception e) {
					throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
				}
			};
		}
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
