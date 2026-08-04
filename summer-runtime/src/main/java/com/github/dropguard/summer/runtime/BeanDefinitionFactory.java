package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Constructs AOP interceptor maps for proxy generation from already-discovered {@link
 * BeanDefinition}s.
 *
 * <p>Discovery itself now lives in {@link com.github.dropguard.summer.core.Discovery}, shared by
 * both the Runtime and AOT engines — this factory only post-processes the unified candidate set
 * (populating interceptor edges used by {@link
 * com.github.dropguard.summer.core.bean.SharedDependencyResolver} for topological ordering).
 *
 * <p>This class is stateless and thread-safe. All methods accept their dependencies as parameters
 * rather than holding mutable state.
 */
final class BeanDefinitionFactory {

    private BeanDefinitionFactory() {}

    /**
     * Builds a map from bean qualifiedName to its matching interceptor qualifiedNames. Uses the
     * pre-computed {@link BeanDefinition#interceptors} list populated by {@link
     * #populateInterceptors(List)}.
     *
     * @param allBeans list of all bean definitions
     * @return map from bean qualifiedName to interceptor qualifiedNames
     */
    public static Map<String, List<String>> buildInterceptorMap(List<BeanDefinition> allBeans) {
        return allBeans.stream()
                .filter(BeanDefinition::needsProxy)
                .filter(bean -> !isInterceptor(bean))
                .map(bean -> Map.entry(bean.qualifiedName, matchingInterceptorNames(bean)))
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Populates {@link BeanDefinition#interceptors} for beans that need AOP proxying. This tells
     * {@link com.github.dropguard.summer.core.bean.SharedDependencyResolver} about AOP interceptor
     * dependencies so that the topological sort places interceptors before their targets.
     *
     * <p>Interceptor matching reads {@link BeanDefinition#interceptorBindingAnnotations} —
     * pre-computed at discovery time by {@link com.github.dropguard.summer.core.Discovery} — rather
     * than scanning annotations via reflection.
     *
     * @param allBeans list of all bean definitions
     */
    public static void populateInterceptors(List<BeanDefinition> allBeans) {
        List<BeanDefinition> interceptors = findInterceptors(allBeans);
        if (interceptors.isEmpty()) {
            return;
        }
        for (BeanDefinition bean : allBeans) {
            // needsProxy already excludes @Interceptor beans -- keep isInterceptor for
            // clarity
            if (!bean.needsProxy() || bean.isInterceptor) {
                continue;
            }
            // Pure string Set intersection on pre-computed interceptorBindingAnnotations --
            // no reflection
            interceptors.stream()
                    .filter(ib -> ib != bean)
                    .filter(ib -> hasMatchingBinding(ib, bean))
                    .forEach(ib -> bean.interceptors.add(ib));
        }
    }

    /**
     * Checks if an interceptor definition has a binding annotation that matches the target
     * definition. Pure string Set intersection on pre-computed {@link
     * BeanDefinition#interceptorBindingAnnotations} — no reflection.
     */
    public static boolean hasMatchingBinding(
            BeanDefinition interceptorDef, BeanDefinition targetDef) {
        return interceptorDef.interceptorBindingAnnotations.stream()
                .anyMatch(targetDef.interceptorBindingAnnotations::contains);
    }

    // ---- internal helpers ----

    private static List<BeanDefinition> findInterceptors(List<BeanDefinition> allBeans) {
        return allBeans.stream().filter(BeanDefinitionFactory::isInterceptor).toList();
    }

    private static boolean isInterceptor(BeanDefinition bean) {
        return bean.isInterceptor;
    }

    /** Returns the qualified names of interceptors that match the given bean. */
    private static List<String> matchingInterceptorNames(BeanDefinition bean) {
        return bean.interceptors.stream().map(b -> b.qualifiedName).toList();
    }
}
