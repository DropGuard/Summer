package summer.web;

import summer.core.ApplicationContext;
import summer.core.Component;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;

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
        context.getComponentClasses().stream()
                .filter(clazz -> clazz.isAnnotationPresent(RestController.class) ||
                        clazz.isAnnotationPresent(Component.class))
                .forEach(this::registerController);
    }

    private void registerController(Class<?> clazz) {
        // Get the instance from the application context
        Object instance = context.getBean(clazz);

        // Get all methods with HTTP method annotations
        Arrays.stream(clazz.getMethods())
                .forEach(method -> registerHandler(clazz, instance, method));
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

        router.register(httpMethod, path, (request, response) -> {
            try {
                // Enforce exactly one parameter of type Request
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != 1 || !paramTypes[0].equals(Request.class)) {
                    throw new summer.core.SummerException(
                            "Controller method " + method.getName() + " in " + clazz.getName() +
                                    " MUST take exactly ONE parameter of type summer.web.Request.");
                }

                return method.invoke(instance, request);
            } catch (Exception e) {
                if (e instanceof java.lang.reflect.InvocationTargetException) {
                    Throwable cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                    response.error(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                } else {
                    response.error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
                return null;
            }
        });

        System.out.println("Route registered: " + httpMethod + " " + path);
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