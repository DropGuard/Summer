package com.github.dropguard.summer.runtime.web;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.spi.RouteRegistrar;
import com.github.dropguard.summer.core.spi.RouteRegistry;
import com.github.dropguard.summer.web.annotation.Delete;
import com.github.dropguard.summer.web.annotation.Get;
import com.github.dropguard.summer.web.annotation.PathParam;
import com.github.dropguard.summer.web.annotation.Post;
import com.github.dropguard.summer.web.annotation.Put;
import com.github.dropguard.summer.web.annotation.QueryParam;
import com.github.dropguard.summer.web.annotation.RestController;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web route scanner that scans {@code @RestController} beans and registers their routes via the
 * core SPI.
 *
 * <p>Loaded by {@link java.util.ServiceLoader} (registered in this module's {@code
 * META-INF/services}) whenever routes are collected at container build time: by the Runtime engine
 * at runtime, and by the AOT engine during {@code wire()} generation. If {@code summer-runtime-web}
 * is not on the classpath, no routes are registered and the container runs in pure-DI mode.
 *
 * <p>This class uses reflection — it lives in the runtime-web bridge (not in summer-web) so the web
 * module stays a reflection-free contract layer; the reflection confinement rule permits {@code
 * ..summer.runtime..} only. The AOT engine keeps its own compile-time (Jandex-based) route
 * discovery; this scanner is the SPI-level behavior-compatibility backstop shared by both engines.
 */
@Internal
public class WebRouteScanner implements RouteRegistrar {

    private static final Logger log = LoggerFactory.getLogger(WebRouteScanner.class);

    @Override
    public void register(RouteRegistry registry, List<BeanDefinition> beans) {
        log.debug("[Summer] WebRouteScanner scanning {} beans for web routes", beans.size());
        for (BeanDefinition bean : beans) {
            try {
                Class<?> clazz = Class.forName(bean.qualifiedName);
                registerRoutesForClass(clazz, bean, registry);
                registerExceptionHandlersForClass(clazz, bean, registry);
            } catch (ClassNotFoundException e) {
                log.warn(
                        "[Summer] Cannot load class {} for web scanning — skipping",
                        bean.qualifiedName);
            }
        }
    }

    // ── Route scanning ────────────────────────────────────────────────────

    private void registerRoutesForClass(
            Class<?> clazz, BeanDefinition bean, RouteRegistry registry) {
        // Check for @RestController
        RestController restController = clazz.getAnnotation(RestController.class);
        if (restController == null) {
            return;
        }

        String basePath = restController.value();

        for (Method method : clazz.getDeclaredMethods()) {
            String httpMethod = resolveHttpMethod(method);
            if (httpMethod == null) {
                continue;
            }

            String methodPath = extractMethodPath(method);
            String fullPath = combinePaths(basePath, methodPath);
            String returnType = method.getReturnType().getName();

            // Enforce Gin-style contract: controller methods must return void
            if (!"void".equals(returnType)) {
                throw new IllegalStateException(
                        clazz.getName()
                                + "."
                                + method.getName()
                                + "() must return void. "
                                + "Controller methods follow the Gin pattern: write to the "
                                + "context, do not return a value.");
            }

            // Enforce Gin-style contract: first parameter must be HttpContext
            Parameter[] handlerParams = method.getParameters();
            if (handlerParams.length == 0
                    || !handlerParams[0]
                            .getType()
                            .getName()
                            .equals("com.github.dropguard.summer.web.HttpContext")) {
                throw new IllegalStateException(
                        clazz.getName()
                                + "."
                                + method.getName()
                                + "() must declare HttpContext as its first parameter. "
                                + "All controller methods follow the Gin pattern: "
                                + "first arg is always the context.");
            }

            // Build parameter list
            java.util.List<RouteRegistry.ParamInfo> params = collectParameters(method);

            registry.registerRoute(bean, httpMethod, fullPath, method.getName(), params);
        }
    }

    private String resolveHttpMethod(Method method) {
        if (method.isAnnotationPresent(Get.class)) {
            return "GET";
        }
        if (method.isAnnotationPresent(Post.class)) {
            return "POST";
        }
        if (method.isAnnotationPresent(Put.class)) {
            return "PUT";
        }
        if (method.isAnnotationPresent(Delete.class)) {
            return "DELETE";
        }
        return null;
    }

    private String extractMethodPath(Method method) {
        if (method.isAnnotationPresent(Get.class)) {
            return method.getAnnotation(Get.class).value();
        }
        if (method.isAnnotationPresent(Post.class)) {
            return method.getAnnotation(Post.class).value();
        }
        if (method.isAnnotationPresent(Put.class)) {
            return method.getAnnotation(Put.class).value();
        }
        if (method.isAnnotationPresent(Delete.class)) {
            return method.getAnnotation(Delete.class).value();
        }
        return "";
    }

    private java.util.List<RouteRegistry.ParamInfo> collectParameters(Method method) {
        java.util.List<RouteRegistry.ParamInfo> params = new java.util.ArrayList<>();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();
            String paramName = param.getName();

            // Skip HttpContext
            if (paramType.getName().equals("com.github.dropguard.summer.web.HttpContext")) {
                continue;
            }

            // Check parameter annotations
            boolean validated = param.isAnnotationPresent(Valid.class);

            if (param.isAnnotationPresent(PathParam.class)) {
                PathParam pathParam = param.getAnnotation(PathParam.class);
                String bindingName = pathParam.value().isEmpty() ? paramName : pathParam.value();
                params.add(
                        new RouteRegistry.ParamInfo(
                                paramName,
                                bindingName,
                                RouteRegistry.ParamBinding.PATH,
                                paramType,
                                true, // PathParam is always required by default
                                null,
                                validated));
            } else if (param.isAnnotationPresent(QueryParam.class)) {
                QueryParam queryParam = param.getAnnotation(QueryParam.class);
                String bindingName = queryParam.value().isEmpty() ? paramName : queryParam.value();
                params.add(
                        new RouteRegistry.ParamInfo(
                                paramName,
                                bindingName,
                                RouteRegistry.ParamBinding.QUERY,
                                paramType,
                                true, // default required, but can be overridden? We'll keep it
                                // simple
                                null,
                                validated));
            } else if (com.github.dropguard.summer.web.ScrollRequest.class.isAssignableFrom(
                    paramType)) {
                params.add(
                        new RouteRegistry.ParamInfo(
                                paramName,
                                "",
                                RouteRegistry.ParamBinding.PAGEABLE,
                                paramType,
                                false,
                                null));
            } else {
                // Default: body
                params.add(
                        new RouteRegistry.ParamInfo(
                                paramName,
                                "",
                                RouteRegistry.ParamBinding.BODY,
                                paramType,
                                false,
                                null,
                                validated));
            }
        }
        return params;
    }

    private String combinePaths(String base, String method) {
        if (base.isEmpty()) {
            return method;
        }
        if (method.isEmpty()) {
            return base;
        }
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedMethod = method.startsWith("/") ? method : "/" + method;
        return normalizedBase + normalizedMethod;
    }

    // ── Exception handler scanning ──────────────────────────────────────

    private void registerExceptionHandlersForClass(
            Class<?> clazz, BeanDefinition bean, RouteRegistry registry) {
        for (Method method : clazz.getDeclaredMethods()) {
            com.github.dropguard.summer.web.annotation.ExceptionHandler ann =
                    method.getAnnotation(
                            com.github.dropguard.summer.web.annotation.ExceptionHandler.class);
            if (ann != null) {
                String exClass = ann.value().getName();
                registry.registerExceptionHandler(
                        bean, method.getName(), exClass, method.getParameterCount());
            }
        }
    }
}
