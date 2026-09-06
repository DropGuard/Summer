package com.github.dropguard.summer.core.bean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.exception.BeanCreationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tests for the AOP concrete-class injection guard in {@link SharedDependencyResolver}: a proxied
 * bean must never be injected by its concrete class — the concrete-class registration holds the
 * raw, interceptor-less instance, so such an injection silently bypasses AOP. The resolver is
 * shared by both DI engines, so one guard covers Runtime and AOT alike.
 */
class SharedDependencyResolverTest {

    private static BeanDefinition component(String name, Set<String> bindingAnnotations) {
        BeanDefinition bd = new BeanDefinition(name, name.substring(name.lastIndexOf('.') + 1));
        bd.interceptorBindingAnnotations = bindingAnnotations;
        return bd;
    }

    private static InjectionParameter param(String typeName) {
        return new InjectionParameter(typeName, new ArrayList<>());
    }

    void concreteClassInjectionOfProxiedBeanFailsFast() {
        BeanDefinition proxied = component("pkg.UserService", Set.of("pkg.Transactional"));
        proxied.interfaceNames.add("pkg.UserApi");
        BeanDefinition dependent = component("pkg.UserController", Set.of());
        dependent.parameters.add(param("pkg.UserService"));

        assertThrows(
                BeanCreationException.class,
                () ->
                        new SharedDependencyResolver()
                                .resolve(List.of(proxied, dependent), List.of()),
                "injecting an AOP-proxied bean by its concrete class must be rejected");
    }

    void interfaceInjectionOfProxiedBeanIsAllowed() {
        BeanDefinition proxied = component("pkg.UserService", Set.of("pkg.Transactional"));
        proxied.interfaceNames.add("pkg.UserApi");
        BeanDefinition dependent = component("pkg.UserController", Set.of());
        dependent.parameters.add(param("pkg.UserApi"));

        assertDoesNotThrow(
                () ->
                        new SharedDependencyResolver()
                                .resolve(List.of(proxied, dependent), List.of()),
                "injecting a proxied bean through its interface delivers the proxy and is valid");
    }

    void concreteClassInjectionOfUnproxiedBeanIsAllowed() {
        BeanDefinition plain = component("pkg.PlainService", Set.of());
        BeanDefinition dependent = component("pkg.PlainController", Set.of());
        dependent.parameters.add(param("pkg.PlainService"));

        assertDoesNotThrow(
                () -> new SharedDependencyResolver().resolve(List.of(plain, dependent), List.of()),
                "a non-proxied bean may be injected by its concrete class");
    }

    void beanContainerInjectionIsRejectedAtDiscovery() {
        // Rejected here, at discovery time — the engine-side checks were removed as unreachable,
        // so this is the single guard for both engines.
        BeanDefinition dependent = component("pkg.ContainerInjecting", Set.of());
        dependent.parameters.add(param("com.github.dropguard.summer.core.BeanContainer"));

        assertThrows(
                BeanCreationException.class,
                () -> new SharedDependencyResolver().resolve(List.of(dependent), List.of()),
                "BeanContainer constructor injection must fail at discovery, before any engine");
    }

    void independentBeansAreOrderedByQualifiedNameNotByHashMapIteration() {
        // BeanDefinition has identity hashCode, so the Kahn queue seeded from a HashMap keyed by
        // BeanDefinition iterated in identity-hash order — bean creation order (and thus reverse
        // teardown, route registration, generated AOT output) drifted between JVM runs. The
        // pinned contract: independent (zero-indegree) beans come out in qualifiedName order.
        List<BeanDefinition> beans = new ArrayList<>();
        for (String name :
                new String[] {"pkg.Zebra", "pkg.Mango", "pkg.Alpha", "pkg.Kiwi", "pkg.Batch"}) {
            beans.add(component(name, Set.of()));
        }

        List<BeanDefinition> sorted = new SharedDependencyResolver().resolve(beans, List.of());

        assertEquals(
                List.of("pkg.Alpha", "pkg.Batch", "pkg.Kiwi", "pkg.Mango", "pkg.Zebra"),
                sorted.stream().map(b -> b.qualifiedName).toList(),
                "independent beans must be ordered deterministically by qualifiedName");
    }

    void collectionInjectionExcludesTheDependentItself() {
        // Composite pattern: a bean implementing T that injects List<T>. Including the
        // dependent among its own collection matches self-edged the graph and died as a
        // false CircularDependencyException at startup.
        BeanDefinition first = component("pkg.ChainA", Set.of());
        first.interfaceNames.add("pkg.Middleware");
        BeanDefinition second = component("pkg.ChainB", Set.of());
        second.interfaceNames.add("pkg.Middleware");
        BeanDefinition composite = component("pkg.ChainBuilder", Set.of());
        composite.interfaceNames.add("pkg.Middleware");
        composite.parameters.add(param("java.util.List<pkg.Middleware>"));

        List<BeanDefinition> sorted =
                new SharedDependencyResolver()
                        .resolve(List.of(first, second, composite), List.of());

        assertEquals(
                List.of("pkg.ChainA", "pkg.ChainB", "pkg.ChainBuilder"),
                sorted.stream().map(b -> b.qualifiedName).toList(),
                "the composite must resolve after its (other) implementors");

        InjectionParameter listParam = composite.parameters.get(0);
        assertEquals(
                List.of(first, second),
                listParam.resolved(),
                "a bean's own List<T> slice must exclude the bean itself");
    }
}
