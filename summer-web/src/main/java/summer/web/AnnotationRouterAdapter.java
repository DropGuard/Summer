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
		Handler handler = createFastHandler(instance, method);
		router.register(httpMethod, path, handler);
		System.out.println("Route registered (Pure Fast Dispatch): " + httpMethod + " " + path);
	}

	private Handler createFastHandler(Object instance, Method method) {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();
			MethodHandle methodHandle = lookup.unreflect(method).bindTo(instance);

			// Source: (P1, P2...) -> R
			MethodType sourceType = methodHandle.type();
			MethodHandle adaptedHandle = methodHandle;

			// 1. Handle void return type: convert to "" to avoid 404
			if (method.getReturnType().equals(void.class)) {
				MethodHandle constantEmpty = MethodHandles.constant(Object.class, "");
				adaptedHandle = MethodHandles.filterReturnValue(methodHandle, MethodHandles.dropArguments(constantEmpty, 0, void.class));
			} else {
				adaptedHandle = adaptedHandle.asType(sourceType.changeReturnType(Object.class));
			}

			// 2. Adapt parameters: (WebContext) -> (P1, P2...)
			Class<?>[] paramTypes = method.getParameterTypes();
			int[] reorder = new int[paramTypes.length];
			for (int i = 0; i < paramTypes.length; i++) {
				Class<?> p = paramTypes[i];
				if (p.equals(WebContext.class)) {
					reorder[i] = 0;
				} else if (p.equals(Request.class)) {
					MethodHandle getReq = lookup.findVirtual(WebContext.class, "request", MethodType.methodType(Request.class));
					adaptedHandle = MethodHandles.filterArguments(adaptedHandle, i, getReq);
					reorder[i] = 0;
				} else if (p.equals(Response.class)) {
					MethodHandle getResp = lookup.findVirtual(WebContext.class, "response", MethodType.methodType(Response.class));
					adaptedHandle = MethodHandles.filterArguments(adaptedHandle, i, getResp);
					reorder[i] = 0;
				} else {
					throw new UnsupportedOperationException("Unsupported: " + p.getName());
				}
			}
			
			adaptedHandle = MethodHandles.permuteArguments(adaptedHandle, MethodType.methodType(Object.class, WebContext.class), reorder);

			CallSite site = LambdaMetafactory.metafactory(
					lookup, "handle", MethodType.methodType(Handler.class),
					MethodType.methodType(Object.class, WebContext.class),
					adaptedHandle, MethodType.methodType(Object.class, WebContext.class)
			);

			return (Handler) site.getTarget().invoke();
		} catch (Throwable t) {
			// Reflection fallback
			return ctx -> {
				Object[] args = new Object[method.getParameterCount()];
				Class<?>[] paramTypes = method.getParameterTypes();
				for (int i = 0; i < paramTypes.length; i++) {
					if (paramTypes[i].equals(WebContext.class)) args[i] = ctx;
					else if (paramTypes[i].equals(Request.class)) args[i] = ctx.request();
					else if (paramTypes[i].equals(Response.class)) args[i] = ctx.response();
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