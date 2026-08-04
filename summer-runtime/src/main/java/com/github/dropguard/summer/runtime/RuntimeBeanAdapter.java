package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.annotation.Replaces;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.RouteInfo;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts runtime reflection objects (Class/Method) to the unified BeanDefinition model.
 *
 * <p>This is the bridge between the runtime engine and the unified metadata model. The runtime
 * engine discovers beans via reflection, but downstream algorithms (condition evaluation,
 * dependency resolution) operate on BeanDefinitions.
 */
@Internal
public final class RuntimeBeanAdapter {

    private static final Logger log = LoggerFactory.getLogger(RuntimeBeanAdapter.class);

    public RuntimeBeanAdapter() {}

    /** Adapts a @Component class to a BeanDefinition. */
    private BeanDefinition createBaseDefinition(Class<?> beanType) {
        BeanDefinition bean = new BeanDefinition(beanType.getName(), beanType.getSimpleName());
        collectInterfaces(beanType, bean.interfaceNames, new HashSet<>());
        return bean;
    }

    public BeanDefinition adaptComponent(Class<?> clazz) {
        BeanDefinition bean = createBaseDefinition(clazz);

        // Constructor parameters
        Constructor<?> ctor = findSinglePublicConstructor(clazz);
        if (ctor != null) {
            java.lang.reflect.Type[] genericTypes = ctor.getGenericParameterTypes();
            Class<?>[] rawTypes = ctor.getParameterTypes();
            for (int i = 0; i < rawTypes.length; i++) {
                Class<?> raw = rawTypes[i];
                if (raw == java.util.List.class
                        && genericTypes[i] instanceof java.lang.reflect.ParameterizedType pt
                        && pt.getActualTypeArguments()[0] instanceof Class<?> ec) {
                    bean.addParameter("java.util.List<" + ec.getName() + ">");
                } else if (raw == java.util.List.class
                        && genericTypes[i] instanceof java.lang.reflect.ParameterizedType pt
                        && pt.getActualTypeArguments()[0]
                                instanceof java.lang.reflect.ParameterizedType) {
                    throw new com.github.dropguard.summer.core.exception
                            .UnsupportedInjectionException(
                            "Nested generic type injection is not supported: List<"
                                    + genericTypes[i].getTypeName()
                                    + "> in "
                                    + bean.qualifiedName);
                } else {
                    bean.addParameter(raw.getName());
                }
            }
        }

        // AOP binding metadata: collect @InterceptorBinding annotations for ALL beans.
        // This single pass replaces the old two-step approach (separate boolean check
        // + separate @Interceptor annotation scan). The result feeds pure-String Set
        // intersection matching downstream -- no reflection on the matching path.
        collectAopBindings(clazz, bean);

        // Route metadata (if Controller)
        collectRoutes(clazz, bean);

        // Exception handler metadata
        collectExceptionHandlers(clazz, bean);

        // @ConditionalOnBean
        collectConditions(clazz, bean);

        log.debug(
                "[Summer] Adapted component: {} (interfaces={})",
                clazz.getSimpleName(),
                bean.interfaceNames.size());

        return bean;
    }

    /** Adapts a @Bean method to a BeanDefinition with factory method fields. */
    public BeanDefinition adaptFactoryMethod(Method method) {
        BeanDefinition bean = createBaseDefinition(method.getReturnType());

        bean.configClassName = method.getDeclaringClass().getName();
        bean.producerMethodName = method.getName();
        java.lang.reflect.Type[] genericTypes = method.getGenericParameterTypes();
        Class<?>[] rawTypes = method.getParameterTypes();
        for (int i = 0; i < rawTypes.length; i++) {
            Class<?> param = rawTypes[i];
            if (param == java.util.List.class
                    && genericTypes[i] instanceof java.lang.reflect.ParameterizedType pt
                    && pt.getActualTypeArguments()[0] instanceof Class<?> ec) {
                bean.addParameter("java.util.List<" + ec.getName() + ">");
            } else if (param == java.util.List.class
                    && genericTypes[i] instanceof java.lang.reflect.ParameterizedType pt
                    && pt.getActualTypeArguments()[0]
                            instanceof java.lang.reflect.ParameterizedType) {
                throw new com.github.dropguard.summer.core.exception.UnsupportedInjectionException(
                        "Nested generic type injection is not supported: List<"
                                + genericTypes[i].getTypeName()
                                + "> in "
                                + bean.qualifiedName);
            } else {
                bean.addParameter(param.getName());
            }
        }

        log.debug(
                "[Summer] Adapted factory method: {}.{}() -> {}",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                method.getReturnType().getSimpleName());

        // Method-level @Replaces (populated during discovery so
        // SharedConditionEvaluator
        // doesn't need to re-read Jandex)
        Replaces methodReplaces = method.getAnnotation(Replaces.class);
        if (methodReplaces != null) {
            bean.methodLevelReplaces = methodReplaces.value().getName();
        }

        // Method-level @ConditionalOnBean
        com.github.dropguard.summer.core.annotation.ConditionalOnBean methodConditional =
                method.getAnnotation(
                        com.github.dropguard.summer.core.annotation.ConditionalOnBean.class);
        if (methodConditional != null) {
            bean.methodConditionalOnBeanType = methodConditional.value().getName();
        }

        return bean;
    }

    /**
     * Adapts a @ConfigMapping interface to a ConfigPropertiesBean marker. The marker carries the
     * prefix and lets SharedDependencyResolver resolve the config type when other beans inject it;
     * binding itself is performed by RuntimeContainer.bindConfiguration, which reads
     * the @WithDefault metadata directly (no per-engine defaults map).
     */
    public ConfigPropertiesBean adaptConfigProperties(Class<?> clazz, String prefix) {
        ConfigPropertiesBean bean =
                new ConfigPropertiesBean(clazz.getName(), clazz.getSimpleName());
        bean.configPropertiesPrefix = prefix;
        return bean;
    }

    // ---- Private helpers ----

    private Constructor<?> findSinglePublicConstructor(Class<?> clazz) {
        Constructor<?>[] ctors = clazz.getConstructors();
        if (ctors.length != 1) {
            log.warn(
                    "[Summer] Component {} must have exactly ONE public constructor. Found: {}",
                    clazz.getName(),
                    ctors.length);
            return null;
        }
        return ctors[0];
    }

    private void collectInterfaces(Class<?> clazz, List<String> target, Set<String> visited) {
        for (Class<?> iface : clazz.getInterfaces()) {
            if (visited.add(iface.getName())) {
                target.add(iface.getName());
                collectInterfaces(iface, target, visited);
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            collectInterfaces(superClass, target, visited);
        }
    }

    private void collectAopBindings(Class<?> clazz, BeanDefinition bean) {
        Set<String> bindings = new HashSet<>();
        // Check class-level annotations for @InterceptorBinding
        for (Annotation ann : clazz.getAnnotations()) {
            if (ann.annotationType()
                    .isAnnotationPresent(
                            com.github.dropguard.summer.aop.InterceptorBinding.class)) {
                bindings.add(ann.annotationType().getName());
            }
        }
        // Check method-level annotations
        for (Method method : clazz.getMethods()) {
            for (Annotation ann : method.getAnnotations()) {
                if (ann.annotationType()
                        .isAnnotationPresent(
                                com.github.dropguard.summer.aop.InterceptorBinding.class)) {
                    bindings.add(ann.annotationType().getName());
                }
            }
        }

        bean.interceptorBindingAnnotations = bindings.isEmpty() ? Collections.emptySet() : bindings;
        bean.isInterceptor =
                clazz.isAnnotationPresent(com.github.dropguard.summer.aop.Interceptor.class);
        // needsProxy is derived from interceptorBindingAnnotations + isInterceptor via
        // BeanDefinition.needsProxy()
    }

    private void collectRoutes(Class<?> clazz, BeanDefinition bean) {
        // Check for @RestController annotation
        if (!clazz.isAnnotationPresent(
                com.github.dropguard.summer.web.annotation.RestController.class)) {
            return;
        }

        com.github.dropguard.summer.web.annotation.RestController restController =
                clazz.getAnnotation(
                        com.github.dropguard.summer.web.annotation.RestController.class);
        String basePath = restController.value();

        for (Method method : clazz.getMethods()) {
            String httpMethod = resolveHttpMethod(method);
            if (httpMethod == null) {
                continue;
            }

            String methodPath = extractMethodPath(method);
            String fullPath = combinePaths(basePath, methodPath);
            String returnType = method.getReturnType().getName();

            RouteInfo route =
                    new RouteInfo(
                            httpMethod, fullPath, clazz.getName(), method.getName(), returnType);

            // Collect parameter info
            collectParameters(method, route);

            bean.routes.add(route);
        }
    }

    private String resolveHttpMethod(Method method) {
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Get.class)) {
            return "GET";
        }
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Post.class)) {
            return "POST";
        }
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Put.class)) {
            return "PUT";
        }
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Delete.class)) {
            return "DELETE";
        }
        return null;
    }

    private String extractMethodPath(Method method) {
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Get.class)) {
            return method.getAnnotation(com.github.dropguard.summer.web.annotation.Get.class)
                    .value();
        }
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Post.class)) {
            return method.getAnnotation(com.github.dropguard.summer.web.annotation.Post.class)
                    .value();
        }
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Put.class)) {
            return method.getAnnotation(com.github.dropguard.summer.web.annotation.Put.class)
                    .value();
        }
        if (method.isAnnotationPresent(com.github.dropguard.summer.web.annotation.Delete.class)) {
            return method.getAnnotation(com.github.dropguard.summer.web.annotation.Delete.class)
                    .value();
        }
        return "";
    }

    private void collectParameters(Method method, RouteInfo route) {
        for (int i = 0; i < method.getParameterCount(); i++) {
            Class<?> paramType = method.getParameterTypes()[i];
            String paramName = method.getParameters()[i].getName();

            // Skip HttpContext
            if (paramType.getName().equals("com.github.dropguard.summer.web.HttpContext")) {
                continue;
            }

            // Check parameter annotations
            if (method.getParameters()[i].isAnnotationPresent(
                    com.github.dropguard.summer.web.annotation.PathParam.class)) {
                com.github.dropguard.summer.web.annotation.PathParam pathParam =
                        method.getParameters()[i].getAnnotation(
                                com.github.dropguard.summer.web.annotation.PathParam.class);
                String bindingName = pathParam.value().isEmpty() ? paramName : pathParam.value();
                boolean validated =
                        method.getParameters()[i].isAnnotationPresent(
                                jakarta.validation.Valid.class);
                route.params.add(
                        new RouteInfo.ParamInfo(
                                paramName,
                                bindingName,
                                paramType.getName(),
                                RouteInfo.ParamBinding.PATH,
                                validated));
            } else if (method.getParameters()[i].isAnnotationPresent(
                    com.github.dropguard.summer.web.annotation.QueryParam.class)) {
                com.github.dropguard.summer.web.annotation.QueryParam queryParam =
                        method.getParameters()[i].getAnnotation(
                                com.github.dropguard.summer.web.annotation.QueryParam.class);
                String bindingName = queryParam.value().isEmpty() ? paramName : queryParam.value();
                boolean validated =
                        method.getParameters()[i].isAnnotationPresent(
                                jakarta.validation.Valid.class);
                route.params.add(
                        new RouteInfo.ParamInfo(
                                paramName,
                                bindingName,
                                paramType.getName(),
                                RouteInfo.ParamBinding.QUERY,
                                validated));
            } else if (com.github.dropguard.summer.web.ScrollRequest.class.isAssignableFrom(
                    paramType)) {
                route.params.add(
                        new RouteInfo.ParamInfo(
                                paramName,
                                "",
                                paramType.getName(),
                                RouteInfo.ParamBinding.PAGEABLE,
                                false));
            } else {
                boolean validated =
                        method.getParameters()[i].isAnnotationPresent(
                                jakarta.validation.Valid.class);
                route.params.add(
                        new RouteInfo.ParamInfo(
                                paramName,
                                "",
                                paramType.getName(),
                                RouteInfo.ParamBinding.BODY,
                                validated));
            }
        }
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

    // ── @ExceptionHandler collection ───────────────────────────────────

    private void collectExceptionHandlers(Class<?> clazz, BeanDefinition bean) {
        for (Method method : clazz.getMethods()) {
            com.github.dropguard.summer.web.annotation.ExceptionHandler ann =
                    method.getAnnotation(
                            com.github.dropguard.summer.web.annotation.ExceptionHandler.class);
            if (ann != null) {
                String exClass = ann.value().getName();
                bean.exceptionHandlerMethods.add(
                        new BeanDefinition.ExceptionHandlerEntry(
                                method.getName(), exClass, method.getParameterCount()));
            }
        }
    }

    // ── @ConditionalOnBean collection ──────────────────────────────────

    private void collectConditions(Class<?> clazz, BeanDefinition bean) {
        com.github.dropguard.summer.core.annotation.ConditionalOnBean cond =
                clazz.getAnnotation(
                        com.github.dropguard.summer.core.annotation.ConditionalOnBean.class);
        if (cond != null) {
            bean.conditionalOnBeanType = cond.value().getName();
        }
    }
}
