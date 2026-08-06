package com.github.dropguard.summer.runtime;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Constructs AOP interceptor maps for proxy generation from already-discovered {@link
 * BeanDefinition}s.
 *
 * <p>Discovery itself now lives in the engine module's shared pipeline, and interceptor matching is
 * done there by {@code BeanEnrichment} (Step 3) — this factory only maps the already-populated
 * {@link BeanDefinition#interceptors} edges for the runtime proxy builder.
 *
 * <p>This class is stateless and thread-safe. All methods accept their dependencies as parameters
 * rather than holding mutable state.
 */
final class BeanDefinitionFactory {

    private BeanDefinitionFactory() {}

    /**
     * Builds a map from bean qualifiedName to its matching interceptor qualifiedNames, from the
     * {@link BeanDefinition#interceptors} list populated at discovery time.
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

    // ---- internal helpers ----

    private static boolean isInterceptor(BeanDefinition bean) {
        return bean.isInterceptor;
    }

    /** Returns the qualified names of interceptors that match the given bean. */
    private static List<String> matchingInterceptorNames(BeanDefinition bean) {
        return bean.interceptors.stream().map(b -> b.qualifiedName).toList();
    }
}
