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
import java.lang.reflect.InvocationTargetException;
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
    // interface name -> number of beans implementing it. Register an interface key only for
    // single-impl interfaces (supports getBean(iface) / ctor injection); multi-impl interfaces
    // are collection-injection strategies (List<X>) resolved via getBeans, never by a shared key.
    private final java.util.Map<String, Integer> ifaceCounts;
    // Birth record: every bean's instantiated form (proxy for AOP-bound beans). Framework
    // registration hooks (exception handlers, routes) consume this instead of getBean.
    private final InstantiatedBeans instantiated;

    BeanInstantiator(
            BeanContainer.Builder builder,
            Map<String, List<String>> interceptorMap,
            Map<String, Set<String>> interceptorBindingAnnotations,
            java.util.Map<String, Integer> ifaceCounts,
            InstantiatedBeans instantiated) {
        this.builder = builder;
        this.interceptorMap = interceptorMap;
        this.interceptorBindings = buildInterceptorBindings(interceptorBindingAnnotations);
        this.ifaceCounts = ifaceCounts;
        this.instantiated = instantiated;
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
            // @PostConstruct runs on the raw instance, before the AOP proxy wrap — lifecycle
            // callbacks are never intercepted (CDI semantics). Products skip it: their producer
            // owns initialization (and enrichment never records a method for them).
            invokePostConstruct(bean, instance);
            registerBean(bean, instance);
        } catch (Exception e) {
            if (e instanceof NoSuchBeanException nse) {
                throw nse;
            }
            if (e instanceof BeanCreationException bce) {
                throw bce;
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

    /**
     * Invokes the {@code @PostConstruct} method recorded during enrichment (CDI config-phase-end
     * callback). The method is public and parameterless by enforcement at enrichment time (AOT
     * parity); {@code getMethod} resolves the most specific declaration in the class hierarchy,
     * matching the enrichment scan, and virtual dispatch runs any subclass override.
     */
    private void invokePostConstruct(BeanDefinition bean, Object instance) {
        String methodName = bean.postConstructMethod;
        if (methodName == null) {
            return;
        }
        try {
            Method method = instance.getClass().getMethod(methodName);
            method.invoke(instance);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new BeanCreationException(
                    "Failed to invoke @PostConstruct on bean: " + bean.qualifiedName, e);
        } catch (InvocationTargetException e) {
            // Unwrap the bean's own failure so the reported cause is the real one, not the
            // reflection wrapper (the framework's fail-fast convention).
            throw new BeanCreationException(
                    "Failed to invoke @PostConstruct on bean: " + bean.qualifiedName,
                    e.getCause() != null ? e.getCause() : e);
        }
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
                // BeanContainer injection is rejected in SharedDependencyResolver at discovery
                // time — every BeanDefinition passes through it, so this branch is unreachable
                // for such a parameter.
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
        List<MethodInterceptor> matchingInterceptors =
                resolveMatchingInterceptors(bean.qualifiedName);
        Object proxy =
                RuntimeAopProcessor.applyProxy(
                        instance, bean, matchingInterceptors, interceptorBindings);
        // AOP lookup contract (one bean, one form): a BOUND bean (proxy != instance) is owned by
        // the container AS ITS PROXY — the proxy sits under the concrete-class key AND under its
        // unique interface keys, so every lookup plane (typed getBean, collection scans) sees
        // exactly one incarnation and interception can never be bypassed by resolving through a
        // different type. The raw instance remains the proxy's private target: @PostConstruct has
        // already run on it above, and close()/seal() forward through the proxy onto it.
        // UNBOUND beans register as themselves — nothing changes for them.
        boolean proxied = proxy != instance;
        // A @Bean-produced instance is owned as a product: the container closes it before its
        // producer (the CDI producer-destruction rule — the product's close may access the
        // producer's alive state).
        boolean isProduct = bean.producerMethodName != null;
        Class<?> clazz = instance.getClass();
        Object incarnation = proxied ? proxy : instance;
        // Birth record — the instantiated form is the bean's ONLY legal form. Handlers and route
        // registration resolve through this record, never through lookup (the concrete-class key
        // of an AOP-bound bean holds the proxy, which getBean rejects by contract).
        instantiated.record(bean.qualifiedName, incarnation);
        if (isProduct) {
            builder.registerProduct(clazz, incarnation);
        } else {
            builder.register(clazz, incarnation);
        }
        // Interface-based AOP: the proxy is registered under every DISCOVERED interface that is
        // unique to this bean — discovery's transitive closure (superclass chains included), the
        // same set the specs were built from. Re-deriving via clazz.getInterfaces() here would
        // silently drop superclass-inherited interfaces from resolution.
        // Multiple beans MAY share an interface (strategy pattern): such keys stay unregistered
        // (count guard below) so getBean(interface) keeps failing loudly on ambiguity — callers
        // use getBeans / the resolver chain for multi-impl strategies; the proxies remain visible
        // to those scans via their concrete-class-key registration above.
        registerDiscoveredInterfaces(bean, proxy, isProduct);
    }

    private void registerDiscoveredInterfaces(
            BeanDefinition bean, Object proxy, boolean isProduct) {
        for (String ifaceName : bean.interfaceNames) {
            Class<?> iface = loadClassForInstantiation(ifaceName);
            Integer count = ifaceCounts.get(ifaceName);
            if (count != null && count == 1) {
                if (isProduct) {
                    builder.registerProduct(iface, proxy);
                } else {
                    builder.register(iface, proxy);
                }
            }
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
