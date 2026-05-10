package summer.web;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import summer.core.ApplicationContext;
import summer.core.Component;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

/**
 * Router adapter that discovers and registers routes from @RestController
 * annotated classes.
 */
@Component
public class AnnotationRouterAdapter {
	private final Router router;
	private final ApplicationContext context;

	public AnnotationRouterAdapter(Router router, ApplicationContext context) {
		this.router = router;
		this.context = context;
	}

	public void registerControllers() {
		context.getComponentClasses().stream().filter(
				clazz -> clazz.isAnnotationPresent(RestController.class) || clazz.isAnnotationPresent(Component.class))
				.forEach(this::registerController);
	}

	private void registerController(Class<?> clazz) {
		// Get the instance from the application context
		Object instance = context.getBean(clazz);

		// Get all methods with HTTP method annotations
		Arrays.stream(clazz.getMethods()).forEach(method -> registerHandler(clazz, instance, method));
	}

	private void registerHandler(Class<?> clazz, Object instance, Method method) {
		// Check for HTTP method annotations
		if (method.isAnnotationPresent(Get.class)) {
			registerRoute(clazz, instance, method, "GET");
		} else if (method.isAnnotationPresent(Post.class)) {
			registerRoute(clazz, instance, method, "POST");
		} else if (method.isAnnotationPresent(Put.class)) {
			registerRoute(clazz, instance, method, "PUT");
		} else if (method.isAnnotationPresent(Delete.class)) {
			registerRoute(clazz, instance, method, "DELETE");
		}
	}

	private void registerRoute(Class<?> clazz, Object instance, Method method, String httpMethod) {
		String path = getRoutePath(clazz, method, httpMethod);
		RouteHandler fastHandler = createFastHandler(clazz, method);

		router.register(httpMethod, path, (request, response) -> {
			try {
				return fastHandler.handle(instance, request, response);
			} catch (Throwable e) {
				if (e instanceof java.lang.reflect.InvocationTargetException ite) {
					Throwable cause = ite.getTargetException();
					response.error(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
				} else {
					response.error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
				}
				return null;
			}
		});

		System.out.println("Route registered (Spring-style Flexible Params): " + httpMethod + " " + path);
	}

	private RouteHandler createFastHandler(Class<?> clazz, Method method) {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			MethodHandle methodHandle = lookup.unreflect(method);

			// The Target Functional Interface is: handle(Object instance, Request req, Response resp)
			// The user method could be: method([Request], [Response])
			
			Class<?>[] paramTypes = method.getParameterTypes();
			
			// Start with the basic Handle: (Instance, P1, P2...)
			// We need to adapt it to: (Instance, Request, Response)
			
			MethodHandle adaptedHandle = methodHandle;
			
			// Strategy:
			// 1. If method wants (Request, Response) -> Already perfect (if in that order)
			// 2. If method wants (Request) -> Drop 'Response' argument
			// 3. If method wants (Response) -> Drop 'Request' argument
			// 4. If method wants () -> Drop both
			
			// For simplicity and alignment with Spring, we'll support specific combinations:
			if (paramTypes.length == 0) {
				// Handler has no params, drop both Request and Response
				adaptedHandle = MethodHandles.dropArguments(methodHandle, 1, Request.class, Response.class);
			} else if (paramTypes.length == 1) {
				if (paramTypes[0].equals(Request.class)) {
					// Drop Response
					adaptedHandle = MethodHandles.dropArguments(methodHandle, 2, Response.class);
				} else if (paramTypes[0].equals(Response.class)) {
					// Drop Request
					adaptedHandle = MethodHandles.dropArguments(methodHandle, 1, Request.class);
				}
			} else if (paramTypes.length == 2) {
				// Handle (Request, Response) or (Response, Request)
				if (paramTypes[0].equals(Response.class) && paramTypes[1].equals(Request.class)) {
					// Swap them
					adaptedHandle = MethodHandles.permuteArguments(methodHandle, 
							MethodType.methodType(Object.class, clazz, Request.class, Response.class), 
							0, 2, 1);
				}
			}

			CallSite site = LambdaMetafactory.metafactory(
					lookup,
					"handle",
					MethodType.methodType(RouteHandler.class),
					MethodType.methodType(Object.class, Object.class, Request.class, Response.class),
					adaptedHandle,
					adaptedHandle.type()
			);

			return (RouteHandler) site.getTarget().invoke();
		} catch (Throwable t) {
			// Fallback
			return (instance, request, response) -> {
				Object[] args = Arrays.stream(method.getParameterTypes())
						.map(p -> p.equals(Request.class) ? request : (p.equals(Response.class) ? response : null))
						.toArray();
				return method.invoke(instance, args);
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