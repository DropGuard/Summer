package com.github.dropguard.summer.core.bean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.core.exception.BeanCreationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

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

    @Test
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

    @Test
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

    @Test
    void concreteClassInjectionOfUnproxiedBeanIsAllowed() {
        BeanDefinition plain = component("pkg.PlainService", Set.of());
        BeanDefinition dependent = component("pkg.PlainController", Set.of());
        dependent.parameters.add(param("pkg.PlainService"));

        assertDoesNotThrow(
                () -> new SharedDependencyResolver().resolve(List.of(plain, dependent), List.of()),
                "a non-proxied bean may be injected by its concrete class");
    }
}
