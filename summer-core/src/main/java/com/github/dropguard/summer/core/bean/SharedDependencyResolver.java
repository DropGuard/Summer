package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.CircularDependencyException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
        return resolve(beans, Set.of());
    }

    /**
     * Resolves dependencies and returns beans in topological order.
     *
     * @param beans bean list (real definitions; mocked types have already been removed by {@code
     *     SharedConditionEvaluator})
     * @param mockedTypeNames fully-qualified names of types replaced by a mock. A dependency on a
     *     mocked type is treated as satisfiable — the mock instance is supplied at instantiation
     *     time (registered before the instantiate loop), so the resolver must not fail the build
     *     for it.
     * @return topologically sorted bean list
     * @throws CircularDependencyException if a cycle is detected
     */
    public List<BeanDefinition> resolve(List<BeanDefinition> beans, Set<String> mockedTypeNames) {
        return resolve(beans, mockedTypeNames, Map.of());
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
     * Builds the mapping from each mocked type name to the names of the interfaces its
     * implementation class implements. Used (without class loading) to satisfy a dependency
     * declared against an interface that the mock implements.
     */
    private static Map<String, Set<String>> mockedInterfaceNames(List<MockedBean> mocks) {
        Map<String, Set<String>> result = new HashMap<>();
        for (MockedBean mocked : mocks) {
            Set<String> ifaces = new HashSet<>();
            for (Class<?> iface : mocked.targetType().getInterfaces()) {
                ifaces.add(iface.getName());
            }
            result.put(mocked.targetTypeName(), ifaces);
        }
        return result;
    }

    private List<BeanDefinition> resolve(
            List<BeanDefinition> beans,
            Set<String> mockedTypeNames,
            Map<String, Set<String>> mockedInterfaces) {
        validateUniqueBeanNames(beans);

        for (BeanDefinition bean : beans) {
            resolveDependencies(bean, beans, mockedTypeNames, mockedInterfaces);
        }

        for (BeanDefinition bean : beans) {
            if (bean.isFactoryMethod()) {
                linkConfigBean(bean, beans);
            }
        }

        return topologicalSort(beans);
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
                parameter.resolved().addAll(matches);
                continue;
            }

            // Scalar (non-List) parameter.
            if (paramType.equals("com.github.dropguard.summer.core.BeanContainer"))
                continue; // container unavailable at build time; engines pass null

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
            parameter.resolved().add(resolved);
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

        List<BeanDefinition> sorted = new ArrayList<>();
        Deque<BeanDefinition> queue = new ArrayDeque<>();
        for (var entry : incoming.entrySet()) {
            if (entry.getValue().isEmpty()) queue.add(entry.getKey());
        }

        while (!queue.isEmpty()) {
            BeanDefinition current = queue.poll();
            sorted.add(current);
            for (var entry : incoming.entrySet()) {
                if (entry.getValue().remove(current)
                        && entry.getValue().isEmpty()
                        && !sorted.contains(entry.getKey())) queue.add(entry.getKey());
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
}
