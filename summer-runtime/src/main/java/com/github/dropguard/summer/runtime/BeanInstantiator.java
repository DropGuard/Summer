package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.aop.MethodInterceptor;
import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.bean.InjectionParameter;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Instantiates beans from {@link BeanDefinition}s.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Constructor injection
 *   <li>{@code @Bean} method invocation
 *   <li>Interface registration
 *   <li>AOP proxy wrapping
 * </ul>
 *
 * <p>Parameter type information is read from the {@link InjectionParameter}s of a {@link
 * BeanDefinition} rather than re-derived via reflection. This ensures that once discovery ({@code
 * Discovery} + {@code BeanEnrichment}) populates a {@link BeanDefinition}, it becomes the single
 * source of truth.
 */
final class BeanInstantiator {

    private final BeanContainer.Builder builder;
    private final Map<String, List<String>> interceptorMap;
    private final Map<Class<?>, Set<Class<? extends Annotation>>> interceptorBindings;

    BeanInstantiator(
            BeanContainer.Builder builder,
            Map<String, List<String>> interceptorMap,
            Map<String, Set<String>> interceptorBindingAnnotations) {
        this.builder = builder;
        this.interceptorMap = interceptorMap;
        this.interceptorBindings = buildInterceptorBindings(interceptorBindingAnnotations);
    }

    /**
     * Converts string-based interceptor binding annotations (from {@link
     * BeanDefinition#interceptorBindingAnnotations}) into a Class-keyed map for use by {@link
     * ProxyFactory}.
     */
    private static Map<Class<?>, Set<Class<? extends Annotation>>> buildInterceptorBindings(
            Map<String, Set<String>> interceptorBindingAnnotations) {
        Map<Class<?>, Set<Class<? extends Annotation>>> result = new HashMap<>();
        for (var entry : interceptorBindingAnnotations.entrySet()) {
            Class<?> interceptorClass = loadClassForInstantiation(entry.getKey());
            Set<String> bindingNames = entry.getValue();
            Set<Class<? extends Annotation>> bindings = new HashSet<>();
            for (String name : bindingNames) {
                Class<?> clazz = loadClassForInstantiation(name);
                bindings.add(clazz.asSubclass(Annotation.class));
            }
            result.put(interceptorClass, Collections.unmodifiableSet(bindings));
        }
        return result;
    }

    /** Instantiates a bean from its definition. */
    void instantiateFromDefinition(BeanDefinition beanDef) {
        // @ConfigMapping beans are already bound and registered by
        // RuntimeContainer.bindConfiguration — skip them here so
        // the binding runs exactly once and no BindingContext leaks into this class.
        if (beanDef instanceof ConfigPropertiesBean) {
            return;
        }
        instantiateBean(beanDef);
    }

    private void instantiateBean(BeanDefinition bean) {
        Class<?> clazz = loadClassForInstantiation(bean.qualifiedName);
        if (builder.peek(clazz) != null) {
            return;
        }
        // Engine-provided (synthetic) beans: register the pre-built instance directly
        // instead of instantiating from a class (Quarkus synthetic bean model).
        if (bean.syntheticInstance != null) {
            registerBean(bean, bean.syntheticInstance);
            return;
        }
        try {
            Object instance;
            if (bean.isFactoryMethod()) {
                instance = invokeFactoryMethod(bean);
            } else {
                instance = createInstance(bean);
            }
            registerBean(bean, instance);
        } catch (Exception e) {
            if (e instanceof NoSuchBeanException nse) {
                throw nse;
            }
            throw new BeanCreationException("Failed to instantiate bean: " + bean.qualifiedName, e);
        }
    }

    private Object invokeFactoryMethod(BeanDefinition fb) throws ReflectiveOperationException {
        Class<?> configClass = loadClassForInstantiation(fb.configClassName);
        Object configBean = builder.getBean(configClass);
        Class<?>[] paramTypes =
                fb.parameters.stream()
                        .map(
                                p ->
                                        p.typeName().startsWith("java.util.List<")
                                                ? List.class
                                                : loadClassForInstantiation(p.typeName()))
                        .toArray(Class[]::new);
        Method producer = configClass.getMethod(fb.producerMethodName, paramTypes);
        Object[] args = resolveArgs(fb.parameters);
        return producer.invoke(configBean, args);
    }

    private Object createInstance(BeanDefinition beanDef) throws ReflectiveOperationException {
        Class<?> clazz = loadClassForInstantiation(beanDef.qualifiedName);
        Constructor<?> constructor = findSinglePublicConstructor(clazz);
        Object[] args = resolveArgs(beanDef.parameters);
        return constructor.newInstance(args);
    }

    private Constructor<?> findSinglePublicConstructor(Class<?> clazz) {
        Constructor<?>[] ctors = clazz.getConstructors();
        if (ctors.length != 1) {
            throw new BeanCreationException(
                    "Component "
                            + clazz.getName()
                            + " must have exactly ONE public constructor. Found: "
                            + ctors.length);
        }
        return ctors[0];
    }

    /**
     * Resolves constructor / {@code @Bean} method arguments from the {@link InjectionParameter}s of
     * a {@link BeanDefinition}. Each parameter owns its type and (for a {@code List}) its element
     * type, so there is no parallel collection to re-derive — the runtime reads the same structure
     * every other consumer does.
     */
    private Object[] resolveArgs(List<InjectionParameter> parameters) {
        Object[] args = new Object[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            InjectionParameter parameter = parameters.get(i);
            if (parameter.typeName().startsWith("java.util.List<")) {
                Class<?> elementClass = loadClassForInstantiation(parameter.elementType());
                args[i] = builder.getBeans(elementClass);
            } else {
                if (parameter.typeName().equals("com.github.dropguard.summer.core.BeanContainer")) {
                    throw new BeanCreationException(
                            "ApplicationContext injection is not supported by the runtime engine."
                                    + " Use BeanContainer from caller.");
                }
                Class<?> paramType = loadClassForInstantiation(parameter.typeName());
                args[i] = builder.getBean(paramType);
            }
        }
        return args;
    }

    private void registerBean(BeanDefinition bean, Object instance) {
        registerRegularBean(bean, instance);
    }

    private void registerRegularBean(BeanDefinition bean, Object instance) {
        Class<?> clazz = instance.getClass();
        List<MethodInterceptor> matchingInterceptors =
                resolveMatchingInterceptors(bean.qualifiedName);
        Object proxy =
                RuntimeAopProcessor.applyProxy(
                        instance, bean, matchingInterceptors, interceptorBindings);
        // Concrete class key keeps the raw instance
        builder.register(clazz, instance);
        // Interfaces get the proxy (first-wins)
        registerAllInterfaces(clazz, proxy);
    }

    private void registerAllInterfaces(Class<?> clazz, Object instance) {
        for (Class<?> iface : clazz.getInterfaces()) {
            builder.register(iface, instance);
            registerAllInterfaces(iface, instance);
        }
    }

    private List<MethodInterceptor> resolveMatchingInterceptors(String beanClassName) {
        List<String> interceptorNames = interceptorMap.getOrDefault(beanClassName, List.of());
        if (interceptorNames.isEmpty()) {
            return List.of();
        }
        List<MethodInterceptor> result = new ArrayList<>();
        for (String interceptorName : interceptorNames) {
            Class<?> interceptorClass = loadClassForInstantiation(interceptorName);
            Object interceptor = builder.getBean(interceptorClass);
            if (interceptor instanceof MethodInterceptor mi) {
                result.add(mi);
            }
        }
        return result;
    }

    private static Class<?> loadClassForInstantiation(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new BeanCreationException("Class not found: " + className, e);
        }
    }
}
