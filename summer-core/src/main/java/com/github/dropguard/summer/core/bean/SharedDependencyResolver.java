mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core.bean;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.CircularDependencyException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayDeque;
mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
mport com.github.dropguard.summer.core.Internal;
import java.util.Deque;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashSet;
mport com.github.dropguard.summer.core.Internal;
import java.util.LinkedHashSet;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.Set;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.Logger;
@Internal
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.LoggerFactory;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
mport com.github.dropguard.summer.core.Internal;
 * Shared dependency resolver used by both Runtime and AOT engines.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Dependency resolution based on {@link BeanDefinition}, including:
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <ul>
mport com.github.dropguard.summer.core.Internal;
 *   <li>Constructor parameter dependency resolution
mport com.github.dropguard.summer.core.Internal;
 *   <li>{@code @Bean} method parameter dependency resolution
mport com.github.dropguard.summer.core.Internal;
 *   <li>{@code @Configuration} class linking
mport com.github.dropguard.summer.core.Internal;
 *   <li>AOP interceptor dependencies (via {@link BeanDefinition#interceptors})
mport com.github.dropguard.summer.core.Internal;
 *   <li>Topological sort with cycle detection (Kahn's algorithm)
mport com.github.dropguard.summer.core.Internal;
 * </ul>
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class SharedDependencyResolver {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Logger log = LoggerFactory.getLogger(SharedDependencyResolver.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Resolves dependencies and returns beans in topological order.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list
mport com.github.dropguard.summer.core.Internal;
     * @return topologically sorted bean list
mport com.github.dropguard.summer.core.Internal;
     * @throws CircularDependencyException if a cycle is detected
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public List<BeanDefinition> resolve(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        return resolve(beans, Set.of());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Resolves dependencies and returns beans in topological order.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list (real definitions; mocked types have already been removed by {@code
mport com.github.dropguard.summer.core.Internal;
     *     SharedConditionEvaluator})
mport com.github.dropguard.summer.core.Internal;
     * @param mockedTypeNames fully-qualified names of types replaced by a mock. A dependency on a
mport com.github.dropguard.summer.core.Internal;
     *     mocked type is treated as satisfiable — the mock instance is supplied at instantiation
mport com.github.dropguard.summer.core.Internal;
     *     time (registered before the instantiate loop), so the resolver must not fail the build
mport com.github.dropguard.summer.core.Internal;
     *     for it.
mport com.github.dropguard.summer.core.Internal;
     * @return topologically sorted bean list
mport com.github.dropguard.summer.core.Internal;
     * @throws CircularDependencyException if a cycle is detected
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public List<BeanDefinition> resolve(List<BeanDefinition> beans, Set<String> mockedTypeNames) {
mport com.github.dropguard.summer.core.Internal;
        return resolve(beans, mockedTypeNames, Map.of());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Resolves dependencies and returns beans in topological order.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <p>This overload derives the mocked-type set and their interface names from the {@link
mport com.github.dropguard.summer.core.Internal;
     * MockedBean} list directly, so a dependency declared against an <em>interface</em> that a
mport com.github.dropguard.summer.core.Internal;
     * mocked implementation class implements is correctly recognised as satisfied by the mock
mport com.github.dropguard.summer.core.Internal;
     * (without loading any classes — AOT-safe). Both engines funnel through here with the same
mport com.github.dropguard.summer.core.Internal;
     * {@code MockedBean} list, which is what keeps mock resolution identical across Runtime and
mport com.github.dropguard.summer.core.Internal;
     * AOT.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list (real definitions; mocked types already removed by {@code
mport com.github.dropguard.summer.core.Internal;
     *     SharedConditionEvaluator})
mport com.github.dropguard.summer.core.Internal;
     * @param mocks mocked beans produced from {@code @Mock} parameters
mport com.github.dropguard.summer.core.Internal;
     * @return topologically sorted bean list
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public List<BeanDefinition> resolve(List<BeanDefinition> beans, List<MockedBean> mocks) {
mport com.github.dropguard.summer.core.Internal;
        Set<String> mockedTypeNames =
mport com.github.dropguard.summer.core.Internal;
                mocks.stream()
mport com.github.dropguard.summer.core.Internal;
                        .map(MockedBean::targetTypeName)
mport com.github.dropguard.summer.core.Internal;
                        .collect(java.util.stream.Collectors.toSet());
mport com.github.dropguard.summer.core.Internal;
        Map<String, Set<String>> mockedInterfaces = mockedInterfaceNames(mocks);
mport com.github.dropguard.summer.core.Internal;
        return resolve(beans, mockedTypeNames, mockedInterfaces);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Builds the mapping from each mocked type name to the names of the interfaces its
mport com.github.dropguard.summer.core.Internal;
     * implementation class implements. Used (without class loading) to satisfy a dependency
mport com.github.dropguard.summer.core.Internal;
     * declared against an interface that the mock implements.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private static Map<String, Set<String>> mockedInterfaceNames(List<MockedBean> mocks) {
mport com.github.dropguard.summer.core.Internal;
        Map<String, Set<String>> result = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (MockedBean mocked : mocks) {
mport com.github.dropguard.summer.core.Internal;
            Set<String> ifaces = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
            for (Class<?> iface : mocked.targetType().getInterfaces()) {
mport com.github.dropguard.summer.core.Internal;
                ifaces.add(iface.getName());
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            result.put(mocked.targetTypeName(), ifaces);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return result;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private List<BeanDefinition> resolve(
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans,
mport com.github.dropguard.summer.core.Internal;
            Set<String> mockedTypeNames,
mport com.github.dropguard.summer.core.Internal;
            Map<String, Set<String>> mockedInterfaces) {
mport com.github.dropguard.summer.core.Internal;
        validateUniqueBeanNames(beans);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            resolveDependencies(bean, beans, mockedTypeNames, mockedInterfaces);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean.isFactoryMethod()) {
mport com.github.dropguard.summer.core.Internal;
                linkConfigBean(bean, beans);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        return topologicalSort(beans);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Validates that no two bean definitions share the same qualified name. Two {@code @Bean}
mport com.github.dropguard.summer.core.Internal;
     * methods returning the same type, or a {@code @Component} and a {@code @Bean} producing the
mport com.github.dropguard.summer.core.Internal;
     * same type, is ambiguous and must be rejected at build time.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private void validateUniqueBeanNames(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        Map<String, String> nameToSource = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            String existing = nameToSource.get(bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
            if (existing == null) {
mport com.github.dropguard.summer.core.Internal;
                nameToSource.put(
mport com.github.dropguard.summer.core.Internal;
                        bean.qualifiedName,
mport com.github.dropguard.summer.core.Internal;
                        bean.isFactoryMethod()
mport com.github.dropguard.summer.core.Internal;
                                ? bean.configClassName + "#" + bean.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                                : bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
            } else if (!existing.equals(
mport com.github.dropguard.summer.core.Internal;
                    bean.isFactoryMethod()
mport com.github.dropguard.summer.core.Internal;
                            ? bean.configClassName + "#" + bean.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                            : bean.qualifiedName)) {
mport com.github.dropguard.summer.core.Internal;
                throw new AmbiguousBeanException(
mport com.github.dropguard.summer.core.Internal;
                        "Multiple beans found for type: "
mport com.github.dropguard.summer.core.Internal;
                                + bean.qualifiedName
mport com.github.dropguard.summer.core.Internal;
                                + " is defined by both "
mport com.github.dropguard.summer.core.Internal;
                                + existing
mport com.github.dropguard.summer.core.Internal;
                                + " and "
mport com.github.dropguard.summer.core.Internal;
                                + (bean.isFactoryMethod()
mport com.github.dropguard.summer.core.Internal;
                                        ? bean.configClassName + "#" + bean.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                                        : bean.qualifiedName));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void resolveDependencies(
mport com.github.dropguard.summer.core.Internal;
            BeanDefinition bean,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> allBeans,
mport com.github.dropguard.summer.core.Internal;
            Set<String> mockedTypeNames,
mport com.github.dropguard.summer.core.Internal;
            Map<String, Set<String>> mockedInterfaces) {
mport com.github.dropguard.summer.core.Internal;
        if (bean instanceof ConfigPropertiesBean) return;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (InjectionParameter parameter : bean.parameters) {
mport com.github.dropguard.summer.core.Internal;
            String paramType = parameter.typeName();
mport com.github.dropguard.summer.core.Internal;
            if (paramType.startsWith("java.util.List<")) {
mport com.github.dropguard.summer.core.Internal;
                // A List<T> dependency resolves to all matching beans (or none for a
mport com.github.dropguard.summer.core.Internal;
                // List<MockedType>, satisfied by the single mock at injection time).
mport com.github.dropguard.summer.core.Internal;
                List<BeanDefinition> matches =
mport com.github.dropguard.summer.core.Internal;
                        findAllBeans(parameter.elementType(), allBeans, mockedTypeNames);
mport com.github.dropguard.summer.core.Internal;
                parameter.resolved().addAll(matches);
mport com.github.dropguard.summer.core.Internal;
                continue;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            // Scalar (non-List) parameter.
mport com.github.dropguard.summer.core.Internal;
            if (paramType.equals("com.github.dropguard.summer.core.BeanContainer"))
mport com.github.dropguard.summer.core.Internal;
                continue; // container unavailable at build time; engines pass null
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            BeanDefinition resolved = findBean(paramType, allBeans);
mport com.github.dropguard.summer.core.Internal;
            if (resolved == null) {
mport com.github.dropguard.summer.core.Internal;
                // A dependency on a mocked type is satisfied by the mock instance, which
mport com.github.dropguard.summer.core.Internal;
                // is registered before the instantiate loop. The resolver must not fail
mport com.github.dropguard.summer.core.Internal;
                // the build for it; the engine resolves it at injection time.
mport com.github.dropguard.summer.core.Internal;
                if (isMocked(paramType, mockedTypeNames, mockedInterfaces)) {
mport com.github.dropguard.summer.core.Internal;
                    continue;
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                throw new NoSuchBeanException(
mport com.github.dropguard.summer.core.Internal;
                        paramType,
mport com.github.dropguard.summer.core.Internal;
                        bean.qualifiedName,
mport com.github.dropguard.summer.core.Internal;
                        registeredTypes(allBeans),
mport com.github.dropguard.summer.core.Internal;
                        nearMisses(paramType, allBeans));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            parameter.resolved().add(resolved);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Distinct registered bean types, for failure diagnostics. */
mport com.github.dropguard.summer.core.Internal;
    private static List<String> registeredTypes(List<BeanDefinition> allBeans) {
mport com.github.dropguard.summer.core.Internal;
        List<String> types = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition b : allBeans) {
mport com.github.dropguard.summer.core.Internal;
            if (!types.contains(b.qualifiedName)) {
mport com.github.dropguard.summer.core.Internal;
                types.add(b.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return types;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /** Registered types whose simple name matches the missing type (spelling/archive hint). */
mport com.github.dropguard.summer.core.Internal;
    private static List<String> nearMisses(String paramType, List<BeanDefinition> allBeans) {
mport com.github.dropguard.summer.core.Internal;
        String want = simpleName(paramType);
mport com.github.dropguard.summer.core.Internal;
        List<String> near = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (String t : registeredTypes(allBeans)) {
mport com.github.dropguard.summer.core.Internal;
            if (simpleName(t).equals(want) && !t.equals(paramType)) {
mport com.github.dropguard.summer.core.Internal;
                near.add(t);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return near;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static String simpleName(String fqcn) {
mport com.github.dropguard.summer.core.Internal;
        int idx = fqcn.lastIndexOf('.');
mport com.github.dropguard.summer.core.Internal;
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * True if the dependency type is directly mocked, or is an interface that one of the mocked
mport com.github.dropguard.summer.core.Internal;
     * implementation classes implements. The latter case is resolved without loading any classes
mport com.github.dropguard.summer.core.Internal;
     * (AOT-safe) using the interface names derived from the {@link MockedBean} list.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private boolean isMocked(
mport com.github.dropguard.summer.core.Internal;
            String paramType,
mport com.github.dropguard.summer.core.Internal;
            Set<String> mockedTypeNames,
mport com.github.dropguard.summer.core.Internal;
            Map<String, Set<String>> mockedInterfaces) {
mport com.github.dropguard.summer.core.Internal;
        if (mockedTypeNames.contains(paramType)) {
mport com.github.dropguard.summer.core.Internal;
            return true;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        for (var entry : mockedInterfaces.entrySet()) {
mport com.github.dropguard.summer.core.Internal;
            if (entry.getValue().contains(paramType)) {
mport com.github.dropguard.summer.core.Internal;
                return true;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return false;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private List<BeanDefinition> findAllBeans(
mport com.github.dropguard.summer.core.Internal;
            String paramType, List<BeanDefinition> allBeans, Set<String> mockedTypeNames) {
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> matches = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition candidate : allBeans) {
mport com.github.dropguard.summer.core.Internal;
            if (candidate.qualifiedName.equals(paramType)) {
mport com.github.dropguard.summer.core.Internal;
                matches.add(candidate);
mport com.github.dropguard.summer.core.Internal;
            } else if (candidate.interfaceNames.contains(paramType)) {
mport com.github.dropguard.summer.core.Internal;
                matches.add(candidate);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        // A List<MockedType> dependency is satisfied by the single mock instance at
mport com.github.dropguard.summer.core.Internal;
        // injection time; the resolver does not need to enumerate it here.
mport com.github.dropguard.summer.core.Internal;
        return matches;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void linkConfigBean(BeanDefinition factoryProduct, List<BeanDefinition> allBeans) {
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition candidate : allBeans) {
mport com.github.dropguard.summer.core.Internal;
            if (candidate.qualifiedName.equals(factoryProduct.configClassName)) {
mport com.github.dropguard.summer.core.Internal;
                factoryProduct.configBeanDefinition = candidate;
mport com.github.dropguard.summer.core.Internal;
                return;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        throw new NoSuchBeanException(
mport com.github.dropguard.summer.core.Internal;
                "Could not find @Configuration bean for factory product: "
mport com.github.dropguard.summer.core.Internal;
                        + factoryProduct.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private BeanDefinition findBean(String paramType, List<BeanDefinition> allBeans) {
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> exactMatches = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition candidate : allBeans) {
mport com.github.dropguard.summer.core.Internal;
            if (candidate.qualifiedName.equals(paramType)) exactMatches.add(candidate);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (exactMatches.size() == 1) return exactMatches.get(0);
mport com.github.dropguard.summer.core.Internal;
        if (exactMatches.size() > 1) {
mport com.github.dropguard.summer.core.Internal;
            throw new AmbiguousBeanException(
mport com.github.dropguard.summer.core.Internal;
                    "Multiple beans found for type: "
mport com.github.dropguard.summer.core.Internal;
                            + paramType
mport com.github.dropguard.summer.core.Internal;
                            + " -> "
mport com.github.dropguard.summer.core.Internal;
                            + exactMatches.stream().map(b -> b.qualifiedName).toList());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> interfaceMatches = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition candidate : allBeans) {
mport com.github.dropguard.summer.core.Internal;
            if (candidate.interfaceNames.contains(paramType)) interfaceMatches.add(candidate);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (interfaceMatches.size() == 1) return interfaceMatches.get(0);
mport com.github.dropguard.summer.core.Internal;
        if (interfaceMatches.size() > 1) {
mport com.github.dropguard.summer.core.Internal;
            throw new AmbiguousBeanException(
mport com.github.dropguard.summer.core.Internal;
                    "Multiple beans found for type: "
mport com.github.dropguard.summer.core.Internal;
                            + paramType
mport com.github.dropguard.summer.core.Internal;
                            + " -> "
mport com.github.dropguard.summer.core.Internal;
                            + interfaceMatches.stream().map(b -> b.qualifiedName).toList());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Topological sort (Kahn's algorithm) with cycle detection ──
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private List<BeanDefinition> topologicalSort(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        Map<BeanDefinition, Set<BeanDefinition>> incoming = buildIncomingEdges(beans);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> sorted = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        Deque<BeanDefinition> queue = new ArrayDeque<>();
mport com.github.dropguard.summer.core.Internal;
        for (var entry : incoming.entrySet()) {
mport com.github.dropguard.summer.core.Internal;
            if (entry.getValue().isEmpty()) queue.add(entry.getKey());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        while (!queue.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            BeanDefinition current = queue.poll();
mport com.github.dropguard.summer.core.Internal;
            sorted.add(current);
mport com.github.dropguard.summer.core.Internal;
            for (var entry : incoming.entrySet()) {
mport com.github.dropguard.summer.core.Internal;
                if (entry.getValue().remove(current)
mport com.github.dropguard.summer.core.Internal;
                        && entry.getValue().isEmpty()
mport com.github.dropguard.summer.core.Internal;
                        && !sorted.contains(entry.getKey())) queue.add(entry.getKey());
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        if (sorted.size() != beans.size()) {
mport com.github.dropguard.summer.core.Internal;
            List<String> circular =
mport com.github.dropguard.summer.core.Internal;
                    beans.stream()
mport com.github.dropguard.summer.core.Internal;
                            .filter(b -> !sorted.contains(b))
mport com.github.dropguard.summer.core.Internal;
                            .map(b -> b.qualifiedName)
mport com.github.dropguard.summer.core.Internal;
                            .toList();
mport com.github.dropguard.summer.core.Internal;
            throw new CircularDependencyException(
mport com.github.dropguard.summer.core.Internal;
                    "Circular dependencies detected among: " + circular);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        return sorted;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Map<BeanDefinition, Set<BeanDefinition>> buildIncomingEdges(
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        Map<BeanDefinition, Set<BeanDefinition>> incoming = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition b : beans) incoming.put(b, new LinkedHashSet<>());
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition b : beans) {
mport com.github.dropguard.summer.core.Internal;
            for (InjectionParameter parameter : b.parameters) {
mport com.github.dropguard.summer.core.Internal;
                for (BeanDefinition dep : parameter.resolved()) {
mport com.github.dropguard.summer.core.Internal;
                    if (dep != null) incoming.get(b).add(dep);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (b.configBeanDefinition != null) incoming.get(b).add(b.configBeanDefinition);
mport com.github.dropguard.summer.core.Internal;
            if (b.needsProxy()) {
mport com.github.dropguard.summer.core.Internal;
                for (BeanDefinition interceptor : b.interceptors) incoming.get(b).add(interceptor);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return incoming;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
