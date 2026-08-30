package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.CircularDependencyException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared dependency resolver used by both Runtime and AOT engines.
 *
 * <p>Dependency resolution based on {@link BeanDefinition}, including:
 *
 * <ul>
 *   <li>Constructor parameter dependency resolution
 *   <li>{@code @Bean} method parameter dependency resolution
 *   <li>{@code @Configuration} class linking
 *   <li>AOP interceptor dependencies (via {@link BeanDefinition#interceptors})
 *   <li>Topological sort with cycle detection (Kahn's algorithm)
 * </ul>
 */
@Internal
public final class SharedDependencyResolver {

    private static final Logger log = LoggerFactory.getLogger(SharedDependencyResolver.class);

    /**
     * Resolves dependencies and returns beans in topological order.
     *
     * @param beans bean list
     * @return topologically sorted bean list
     * @throws CircularDependencyException if a cycle is detected
     */
    public List<BeanDefinition> resolve(List<BeanDefinition> beans) {
        return resolve(beans, List.of());
    }

    /**
     * Resolves dependencies and returns beans in topological order.
     *
     * <p>This overload derives the mocked-type set and their interface names from the {@link
     * MockedBean} list directly, so a dependency declared against an <em>interface</em> that a
     * mocked implementation class implements is correctly recognised as satisfied by the mock
     * (without loading any classes — AOT-safe). Both engines funnel through here with the same
     * {@code MockedBean} list, which is what keeps mock resolution identical across Runtime and
     * AOT.
     *
     * @param beans bean list (real definitions; mocked types already removed by {@code
     *     SharedConditionEvaluator})
     * @param mocks mocked beans produced from {@code @Mock} parameters
     * @return topologically sorted bean list
     */
    public List<BeanDefinition> resolve(List<BeanDefinition> beans, List<MockedBean> mocks) {
        Set<String> mockedTypeNames =
                mocks.stream()
                        .map(MockedBean::targetTypeName)
                        .collect(java.util.stream.Collectors.toSet());
        Map<String, Set<String>> mockedInterfaces = mockedInterfaceNames(mocks);
        return resolve(beans, mockedTypeNames, mockedInterfaces);
    }

    /**
     * Builds, per mocked type, EVERY type name a consumer may legally declare for injection: the
     * target itself, its full superclass chain, and its transitive interface hierarchy. Consumers
     * are not required to declare the mock's concrete type — a dependency on {@code
     * AbstractService} or {@code ServicePort} is satisfied by an {@code @Mock} of {@code
     * RealService} exactly as it would be by the real bean (the S-09a contract).
     */
    private static Map<String, Set<String>> mockedInterfaceNames(List<MockedBean> mocks) {
        Map<String, Set<String>> result = new HashMap<>();
        for (MockedBean mocked : mocks) {
            Set<String> assignable = new HashSet<>();
            collectAssignableNames(mocked.targetType(), assignable);
            result.put(mocked.targetTypeName(), assignable);
        }
        return result;
    }

    private static void collectAssignableNames(Class<?> type, Set<String> into) {
        if (type == null || !into.add(type.getName())) {
            return;
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectAssignableNames(iface, into);
        }
        collectAssignableNames(type.getSuperclass(), into);
    }

    private List<BeanDefinition> resolve(
            List<BeanDefinition> beans,
            Set<String> mockedTypeNames,
            Map<String, Set<String>> mockedInterfaces) {
        validateUniqueBeanNames(beans);

        for (BeanDefinition bean : beans) {
            if (bean.isFactoryMethod()) {
                linkConfigBean(bean, beans);
            }
        }

        for (BeanDefinition bean : beans) {
            resolveDependencies(bean, beans, mockedTypeNames, mockedInterfaces);
        }

        return topologicalSort(beans);
    }

    /**
     * Counts how many beans implement each interface name (transitive, from {@link
     * BeanDefinition#interfaceNames}). The registration phase uses this to decide whether a bean's
     * interface key is worth registering: an interface implemented by exactly one bean supports
     * single-bean lookup by interface ({@code getBean(iface)} / constructor injection by
     * interface); an interface implemented by multiple beans is a <em>collection-injection
     * strategy</em> (e.g. {@code List<HttpParameterResolver>} in a chain, {@code List<Middleware>})
     * resolved via {@code getBeans}, so the interface key is deliberately NOT registered — the
     * last-writer-wins overwrite on a shared key is meaningless and hides the multi-impl contract.
     *
     * @return map of interface name -> number of beans implementing it
     */
    public static java.util.Map<String, Integer> interfaceImplementationCounts(
            List<BeanDefinition> beans) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (BeanDefinition bean : beans) {
            for (String iface : bean.interfaceNames) {
                counts.merge(iface, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Validates that no two bean definitions share the same qualified name. Two {@code @Bean}
     * methods returning the same type, or a {@code @Component} and a {@code @Bean} producing the
     * same type, is ambiguous and must be rejected at build time.
     */
    private void validateUniqueBeanNames(List<BeanDefinition> beans) {
        Map<String, String> nameToSource = new HashMap<>();
        for (BeanDefinition bean : beans) {
            String existing = nameToSource.get(bean.qualifiedName);
            if (existing == null) {
                nameToSource.put(
                        bean.qualifiedName,
                        bean.isFactoryMethod()
                                ? bean.configClassName + "#" + bean.producerMethodName
                                : bean.qualifiedName);
            } else if (!existing.equals(
                    bean.isFactoryMethod()
                            ? bean.configClassName + "#" + bean.producerMethodName
                            : bean.qualifiedName)) {
                throw new AmbiguousBeanException(
                        "Multiple beans found for type: "
                                + bean.qualifiedName
                                + " is defined by both "
                                + existing
                                + " and "
                                + (bean.isFactoryMethod()
                                        ? bean.configClassName + "#" + bean.producerMethodName
                                        : bean.qualifiedName));
            }
        }
    }

    private void resolveDependencies(
            BeanDefinition bean,
            List<BeanDefinition> allBeans,
            Set<String> mockedTypeNames,
            Map<String, Set<String>> mockedInterfaces) {
        if (bean instanceof ConfigPropertiesBean) return;

        for (InjectionParameter parameter : bean.parameters) {
            String paramType = parameter.typeName();
            if (paramType.startsWith("java.util.List<")) {
                // A List<T> dependency resolves to all matching beans (or none for a
                // List<MockedType>, satisfied by the single mock at injection time).
                List<BeanDefinition> matches =
                        findAllBeans(parameter.elementType(), allBeans, mockedTypeNames);
                for (BeanDefinition match : matches) {
                    rejectConcreteClassInjection(bean, match, parameter.elementType());
                }
                // @Order alignment: the runtime sorts getBeans by instance order
                // (BeanContainer.ORDER_COMPARATOR); sort the discovery-time slice by the captured
                // BeanDefinition.order so the AOT engine emits List<T> in the same order.
                matches.sort(Comparator.comparingInt(b -> b.order));
                parameter.resolved().addAll(matches);
                continue;
            }

            // Scalar (non-List) parameter. Injecting the container into a bean would create a
            // circular bootstrap reference (the container is being built). Rejected here, at
            // discovery time, so both engines fail fast at build rather than at instantiation —
            // this is the same rejection the engines perform (BeanInstantiator /
            // WireMethodGenerator), moved earlier so it cannot diverge.
            try {
                Class<?> clazz = Class.forName(paramType);
                if (BeanContainer.class.isAssignableFrom(clazz)) {
                    throw new BeanCreationException(
                            "Injection of container type "
                                    + clazz.getName()
                                    + " is not supported: "
                                    + bean.qualifiedName
                                    + " declares a container constructor parameter. Use"
                                    + " BeanContainer from the caller instead.");
                }
            } catch (ClassNotFoundException ignored) {
                // class not found on classpath; will be handled as missing bean later
            }

            BeanDefinition resolved = findBean(paramType, allBeans);
            if (resolved == null) {
                // A dependency on a mocked type is satisfied by the mock instance, which
                // is registered before the instantiate loop. The resolver must not fail
                // the build for it; the engine resolves it at injection time.
                if (isMocked(paramType, mockedTypeNames, mockedInterfaces)) {
                    continue;
                }
                throw new NoSuchBeanException(
                        paramType,
                        bean.qualifiedName,
                        registeredTypes(allBeans),
                        nearMisses(paramType, allBeans));
            }
            rejectConcreteClassInjection(bean, resolved, paramType);
            parameter.resolved().add(resolved);
        }
    }

    /**
     * Fail-fast guard for the concrete-class AOP trap: a proxied bean is registered under its
     * concrete class as the RAW instance (interfaces carry the proxy), so injecting it by concrete
     * class would silently hand the dependent a bean with NO interceptors — transactions, auth,
     * etc. all off. JDK dynamic proxies cannot satisfy a concrete type, so the only correct
     * injection is through one of the bean's interfaces.
     */
    private static void rejectConcreteClassInjection(
            BeanDefinition dependent, BeanDefinition resolved, String paramType) {
        if (resolved.needsProxy() && paramType.equals(resolved.qualifiedName)) {
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                    "Bean "
                            + dependent.qualifiedName
                            + " injects "
                            + resolved.qualifiedName
                            + " by its concrete class, but that bean is AOP-proxied (JDK dynamic"
                            + " proxy). The concrete-class registration holds the raw instance"
                            + " without interceptors — inject by one of its interfaces instead:"
                            + " "
                            + (resolved.interfaceNames.isEmpty()
                                    ? "(none)"
                                    : String.join(", ", resolved.interfaceNames)));
        }
    }

    /** Distinct registered bean types, for failure diagnostics. */
    private static List<String> registeredTypes(List<BeanDefinition> allBeans) {
        List<String> types = new ArrayList<>();
        for (BeanDefinition b : allBeans) {
            if (!types.contains(b.qualifiedName)) {
                types.add(b.qualifiedName);
            }
        }
        return types;
    }

    /** Registered types whose simple name matches the missing type (spelling/archive hint). */
    private static List<String> nearMisses(String paramType, List<BeanDefinition> allBeans) {
        String want = simpleName(paramType);
        List<String> near = new ArrayList<>();
        for (String t : registeredTypes(allBeans)) {
            if (simpleName(t).equals(want) && !t.equals(paramType)) {
                near.add(t);
            }
        }
        return near;
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
    }

    /**
     * True if the dependency type is directly mocked, or is an interface that one of the mocked
     * implementation classes implements. The latter case is resolved without loading any classes
     * (AOT-safe) using the interface names derived from the {@link MockedBean} list.
     */
    private boolean isMocked(
            String paramType,
            Set<String> mockedTypeNames,
            Map<String, Set<String>> mockedInterfaces) {
        if (mockedTypeNames.contains(paramType)) {
            return true;
        }
        for (var entry : mockedInterfaces.entrySet()) {
            if (entry.getValue().contains(paramType)) {
                return true;
            }
        }
        return false;
    }

    private List<BeanDefinition> findAllBeans(
            String paramType, List<BeanDefinition> allBeans, Set<String> mockedTypeNames) {
        List<BeanDefinition> matches = new ArrayList<>();
        for (BeanDefinition candidate : allBeans) {
            if (candidate.qualifiedName.equals(paramType)) {
                matches.add(candidate);
            } else if (candidate.interfaceNames.contains(paramType)) {
                matches.add(candidate);
            }
        }
        // A List<MockedType> dependency is satisfied by the single mock instance at
        // injection time; the resolver does not need to enumerate it here.
        return matches;
    }

    private void linkConfigBean(BeanDefinition factoryProduct, List<BeanDefinition> allBeans) {
        for (BeanDefinition candidate : allBeans) {
            if (candidate.qualifiedName.equals(factoryProduct.configClassName)) {
                factoryProduct.configBeanDefinition = candidate;
                return;
            }
        }
        throw new NoSuchBeanException(
                "Could not find @Configuration bean for factory product: "
                        + factoryProduct.qualifiedName);
    }

    private BeanDefinition findBean(String paramType, List<BeanDefinition> allBeans) {
        List<BeanDefinition> exactMatches = new ArrayList<>();
        for (BeanDefinition candidate : allBeans) {
            if (candidate.qualifiedName.equals(paramType)) exactMatches.add(candidate);
        }
        if (exactMatches.size() == 1) return exactMatches.get(0);
        if (exactMatches.size() > 1) {
            throw new AmbiguousBeanException(
                    "Multiple beans found for type: "
                            + paramType
                            + " -> "
                            + exactMatches.stream().map(b -> b.qualifiedName).toList());
        }

        List<BeanDefinition> interfaceMatches = new ArrayList<>();
        for (BeanDefinition candidate : allBeans) {
            if (candidate.interfaceNames.contains(paramType)) interfaceMatches.add(candidate);
        }
        if (interfaceMatches.size() == 1) return interfaceMatches.get(0);
        if (interfaceMatches.size() > 1) {
            throw new AmbiguousBeanException(
                    "Multiple beans found for type: "
                            + paramType
                            + " -> "
                            + interfaceMatches.stream().map(b -> b.qualifiedName).toList());
        }
        return null;
    }

    // ── Topological sort (Kahn's algorithm) with cycle detection ──

    private List<BeanDefinition> topologicalSort(List<BeanDefinition> beans) {
        Map<BeanDefinition, Set<BeanDefinition>> incoming = buildIncomingEdges(beans);
        Map<BeanDefinition, Set<BeanDefinition>> dependents = buildDependentEdges(incoming);

        List<BeanDefinition> sorted = new ArrayList<>();
        Deque<BeanDefinition> queue = new ArrayDeque<>();
        for (var entry : incoming.entrySet()) {
            if (entry.getValue().isEmpty()) queue.add(entry.getKey());
        }

        while (!queue.isEmpty()) {
            BeanDefinition current = queue.poll();
            sorted.add(current);
            // Only the completed node's dependents can become schedulable — O(out-degree)
            // instead of a full scan per node. A dependent is queued exactly once: its
            // incoming set empties exactly when its last dependency completes.
            for (BeanDefinition dependent : dependents.getOrDefault(current, Set.of())) {
                Set<BeanDefinition> deps = incoming.get(dependent);
                deps.remove(current);
                if (deps.isEmpty()) queue.add(dependent);
            }
        }

        if (sorted.size() != beans.size()) {
            List<String> circular =
                    beans.stream()
                            .filter(b -> !sorted.contains(b))
                            .map(b -> b.qualifiedName)
                            .toList();
            throw new CircularDependencyException(
                    "Circular dependencies detected among: " + circular);
        }

        return sorted;
    }

    private Map<BeanDefinition, Set<BeanDefinition>> buildIncomingEdges(
            List<BeanDefinition> beans) {
        Map<BeanDefinition, Set<BeanDefinition>> incoming = new HashMap<>();
        for (BeanDefinition b : beans) incoming.put(b, new LinkedHashSet<>());

        for (BeanDefinition b : beans) {
            for (InjectionParameter parameter : b.parameters) {
                for (BeanDefinition dep : parameter.resolved()) {
                    if (dep != null) incoming.get(b).add(dep);
                }
            }
            if (b.configBeanDefinition != null) incoming.get(b).add(b.configBeanDefinition);
            if (b.needsProxy()) {
                for (BeanDefinition interceptor : b.interceptors) incoming.get(b).add(interceptor);
            }
        }
        return incoming;
    }

    /** Reverse of {@link #buildIncomingEdges}: node → set of nodes that depend on it. */
    private Map<BeanDefinition, Set<BeanDefinition>> buildDependentEdges(
            Map<BeanDefinition, Set<BeanDefinition>> incoming) {
        Map<BeanDefinition, Set<BeanDefinition>> dependents = new HashMap<>();
        for (BeanDefinition b : incoming.keySet()) dependents.put(b, new LinkedHashSet<>());

        for (var entry : incoming.entrySet()) {
            for (BeanDefinition dep : entry.getValue()) {
                Set<BeanDefinition> set = dependents.get(dep);
                if (set == null) {
                    // `dep` was removed earlier in the pipeline — almost always an unsatisfied
                    // @ConditionalOnBean. A silent NPE here would hide which edge is broken;
                    // name both ends instead.
                    throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                            "Bean "
                                    + entry.getKey().qualifiedName
                                    + " depends on "
                                    + dep.qualifiedName
                                    + ", which was removed during container assembly (most likely"
                                    + " its @ConditionalOnBean requirement is not satisfied within"
                                    + " its visibility scope).");
                }
                set.add(entry.getKey());
            }
        }
        return dependents;
    }
}
