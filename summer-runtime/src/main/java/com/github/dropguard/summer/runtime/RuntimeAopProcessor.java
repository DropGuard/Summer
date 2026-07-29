package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.aop.SummerAopException;
import com.github.dropguard.summer.core.ErrorCode;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies AOP proxies to bean instances. The interception plan for each method is derived here from
 * the bean's pre-enriched binding data ({@link BeanDefinition#methodBindingAnnotations}) — the
 * single source of truth produced by the bean enrichment step. No reflection-based binding scanning
 * happens at proxy dispatch; the factory only walks a precomputed map.
 */
final class RuntimeAopProcessor {

    private RuntimeAopProcessor() {}

    /**
     * Wraps {@code instance} in a JDK proxy if any of its interface methods are bound to an
     * interceptor.
     *
     * @param instance the raw bean instance
     * @param bean its enriched definition (carries the method / class binding map)
     * @param matchingInterceptors interceptors whose own binding annotations match this bean
     * @param interceptorBindings interceptor class -&gt; the binding annotation types it honours
     *     (used to materialise the per-method metadata bindings)
     */
    static Object applyProxy(
            Object instance,
            BeanDefinition bean,
            List<MethodInterceptor> matchingInterceptors,
            Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings) {
        if (instance == null
                || instance.getClass()
                        .isAnnotationPresent(com.github.dropguard.summer.aop.Interceptor.class)) {
            return instance;
        }

        List<MethodInterceptor> matching =
                matchingInterceptors.stream().filter(Objects::nonNull).toList();
        if (matching.isEmpty()) {
            return instance;
        }

        // Summer uses JDK dynamic proxies -- requires at least one interface.
        Class<?> clazz = instance.getClass();
        if (clazz.getInterfaces().length == 0) {
            throw new SummerAopException(
                    ErrorCode.AOP_NO_INTERFACE,
                    clazz.getName()
                            + " is annotated with AOP bindings but implements no interfaces. Summer"
                            + " uses JDK dynamic proxies -- extract an interface and inject it by"
                            + " the interface type instead.");
        }

        Map<Method, ProxyFactory.ProxyMethodSpec> specs =
                buildMethodSpecs(bean, matching, interceptorBindings);
        if (specs.isEmpty()) {
            return instance;
        }
        return ProxyFactory.createProxy(instance, specs);
    }

    /**
     * Builds the per-interface-method proxy plan. A class-level binding (key {@code ""} in {@link
     * BeanDefinition#methodBindingAnnotations}) binds every interface method; a method-level entry
     * binds only the method whose name matches. In both cases all matching interceptors are
     * attached — the runtime only needs to know a method is bound and which interceptors run;
     * finer-grained interceptor / binding filtering is the job of the chain. The binding annotation
     * types carried by a method are materialised from their qualified names so interceptors can
     * introspect them via {@link
     * com.github.dropguard.summer.aop.InterceptedMethod#isAnnotationPresent}.
     */
    private static Map<Method, ProxyFactory.ProxyMethodSpec> buildMethodSpecs(
            BeanDefinition bean,
            List<MethodInterceptor> matching,
            Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings) {
        Map<String, Set<String>> methodBindings = bean.methodBindingAnnotations;
        boolean classLevel = methodBindings.containsKey("");
        Set<String> classLevelNames = classLevel ? methodBindings.get("") : Set.of();

        Map<Method, ProxyFactory.ProxyMethodSpec> specs = new HashMap<>();
        for (String ifaceName : bean.interfaceNames) {
            Class<?> iface = loadClass(ifaceName);
            if (iface == null) {
                continue;
            }
            for (Method method : iface.getMethods()) {
                boolean bound = classLevel || methodBindings.containsKey(method.getName());
                if (!bound) {
                    continue;
                }
                Set<String> boundNames =
                        classLevel ? classLevelNames : methodBindings.get(method.getName());
                Set<Class<? extends Annotation>> bindingTypes =
                        materialize(boundNames, interceptorBindings);
                specs.put(method, new ProxyFactory.ProxyMethodSpec(matching, bindingTypes));
            }
        }
        return specs;
    }

    private static Set<Class<? extends Annotation>> materialize(
            Set<String> bindingNames,
            Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings) {
        Set<Class<? extends Annotation>> types = new java.util.HashSet<>();
        for (String name : bindingNames) {
            for (Set<Class<? extends Annotation>> interceptorBindingSet :
                    interceptorBindings.values()) {
                for (Class<? extends Annotation> t : interceptorBindingSet) {
                    if (t.getName().equals(name)) {
                        types.add(t);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(types);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, RuntimeAopProcessor.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
