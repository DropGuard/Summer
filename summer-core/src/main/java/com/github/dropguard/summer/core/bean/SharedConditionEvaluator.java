mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core.bean;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
mport com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
mport com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashSet;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.Set;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.Logger;
mport com.github.dropguard.summer.core.Internal;
import org.slf4j.LoggerFactory;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
/**
@Internal
mport com.github.dropguard.summer.core.Internal;
 * Shared condition evaluator used by both Runtime and AOT engines.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Four-phase evaluation:
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <ol>
mport com.github.dropguard.summer.core.Internal;
 *   <li>Collect {@code @ConditionalOnBean} requirements
mport com.github.dropguard.summer.core.Internal;
 *   <li>Evaluate {@code @ConditionalOnBean} in topological order, remove unsatisfied beans
mport com.github.dropguard.summer.core.Internal;
 *   <li>Resolve {@code @Replaces} (class-level and method-level), remove replaced beans and any
mport com.github.dropguard.summer.core.Internal;
 *       {@code @Bean} product whose {@code @Configuration} was replaced
mport com.github.dropguard.summer.core.Internal;
 * </ol>
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Reads conditions and replaces from {@link BeanDefinition} fields populated during discovery —
mport com.github.dropguard.summer.core.Internal;
 * no Jandex access at evaluation time.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class SharedConditionEvaluator {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Logger log = LoggerFactory.getLogger(SharedConditionEvaluator.class);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public SharedConditionEvaluator() {}
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Evaluates conditions and removes unsatisfied beans.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list (mutated in place)
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void evaluate(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        evaluate(beans, Set.of());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Evaluates conditions with archive-scoped {@code @ConditionalOnBean} visibility. A bean's
mport com.github.dropguard.summer.core.Internal;
     * condition is satisfied only by another bean in the <em>same</em> archive (see {@link
mport com.github.dropguard.summer.core.Internal;
     * BeanDefinition#archiveName}); injection itself remains global. The {@link BeanDeployment} is
mport com.github.dropguard.summer.core.Internal;
     * accepted for symmetry and future cross-archive contracts, but the boundary check uses the
mport com.github.dropguard.summer.core.Internal;
     * {@code archiveName} already assigned during discovery.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list (mutated in place)
mport com.github.dropguard.summer.core.Internal;
     * @param moduleIndex the archive index (for future cross-archive contracts)
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void evaluate(List<BeanDefinition> beans, BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        evaluate(beans, Set.of(), moduleIndex);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Evaluates conditions, removes unsatisfied beans, and removes any bean whose type is mocked by
mport com.github.dropguard.summer.core.Internal;
     * a {@code @Mock} declared on the test.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <p>Mock removal is a <b>discovery-stage type replacement</b> (Quarkus-style): the real
mport com.github.dropguard.summer.core.Internal;
     * implementation is taken out of the candidate set entirely, so it is never instantiated — and
mport com.github.dropguard.summer.core.Internal;
     * because nothing references it, its dependency closure is also never instantiated. This runs
mport com.github.dropguard.summer.core.Internal;
     * identically on both the Runtime and AOT engines (both funnel through this evaluator), which
mport com.github.dropguard.summer.core.Internal;
     * fixes the prior divergence where the AOT path let a concrete-class {@code @Mock} lose to the
mport com.github.dropguard.summer.core.Internal;
     * real bean via registration-order overwrite.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * <p>{@code mockedTypes} holds the declared {@code @Mock} parameter type names (not {@code
mport com.github.dropguard.summer.core.Internal;
     * mock.getClass()}, which for a Mockito proxy differs from the target type). Matching is by
mport com.github.dropguard.summer.core.Internal;
     * exact type name and implemented interfaces, so it works without loading classes — safe for
mport com.github.dropguard.summer.core.Internal;
     * the AOT compile phase.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list (mutated in place)
mport com.github.dropguard.summer.core.Internal;
     * @param mockedTypes type names declared as {@code @Mock} on the test constructor
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void evaluate(List<BeanDefinition> beans, Set<String> mockedTypes) {
mport com.github.dropguard.summer.core.Internal;
        evaluate(beans, mockedTypes, null);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Full evaluation with both mock removal and archive-scoped {@code @ConditionalOnBean}
mport com.github.dropguard.summer.core.Internal;
     * visibility.
mport com.github.dropguard.summer.core.Internal;
     *
mport com.github.dropguard.summer.core.Internal;
     * @param beans bean list (mutated in place)
mport com.github.dropguard.summer.core.Internal;
     * @param mockedTypes type names declared as {@code @Mock} on the test constructor
mport com.github.dropguard.summer.core.Internal;
     * @param moduleIndex the archive index (for future cross-archive contracts; the boundary check
mport com.github.dropguard.summer.core.Internal;
     *     itself uses {@link BeanDefinition#archiveName})
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void evaluate(
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans, Set<String> mockedTypes, BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        removeMockedBeans(beans, mockedTypes);
mport com.github.dropguard.summer.core.Internal;
        Map<String, String> requiredTypes = collectConditionalRequirements(beans);
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> topoOrder = buildTopologicalOrder(beans, requiredTypes);
mport com.github.dropguard.summer.core.Internal;
        resolveConditionalOnBean(beans, topoOrder, requiredTypes, moduleIndex);
mport com.github.dropguard.summer.core.Internal;
        resolveReplaces(beans);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Removes beans whose type (or any implemented interface) is among the mocked types. The mock
mport com.github.dropguard.summer.core.Internal;
     * instance itself is supplied separately at instance-build time; removing the real definition
mport com.github.dropguard.summer.core.Internal;
     * ensures it is never instantiated.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    private void removeMockedBeans(List<BeanDefinition> beans, Set<String> mockedTypes) {
mport com.github.dropguard.summer.core.Internal;
        if (mockedTypes.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            return;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        beans.removeIf(
mport com.github.dropguard.summer.core.Internal;
                bean -> {
mport com.github.dropguard.summer.core.Internal;
                    if (mockedTypes.contains(bean.qualifiedName)) {
mport com.github.dropguard.summer.core.Internal;
                        return true;
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                    for (String iface : bean.interfaceNames) {
mport com.github.dropguard.summer.core.Internal;
                        if (mockedTypes.contains(iface)) {
mport com.github.dropguard.summer.core.Internal;
                            return true;
mport com.github.dropguard.summer.core.Internal;
                        }
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                    return false;
mport com.github.dropguard.summer.core.Internal;
                });
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Collect @ConditionalOnBean requirements ───────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private Map<String, String> collectConditionalRequirements(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        Map<String, String> requiredTypes = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean.conditionalOnBeanType != null) {
mport com.github.dropguard.summer.core.Internal;
                requiredTypes.put(bean.qualifiedName, bean.conditionalOnBeanType);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (bean.methodConditionalOnBeanType != null) {
mport com.github.dropguard.summer.core.Internal;
                requiredTypes.put(bean.qualifiedName, bean.methodConditionalOnBeanType);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return requiredTypes;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Topological sort ──────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private List<BeanDefinition> buildTopologicalOrder(
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans, Map<String, String> requiredTypes) {
mport com.github.dropguard.summer.core.Internal;
        Map<BeanDefinition, Set<BeanDefinition>> deps = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            String required = requiredTypes.get(bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
            if (required == null) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            Set<BeanDefinition> matches = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
            for (BeanDefinition other : beans) {
mport com.github.dropguard.summer.core.Internal;
                if (other.qualifiedName.equals(required)) {
mport com.github.dropguard.summer.core.Internal;
                    matches.add(other);
mport com.github.dropguard.summer.core.Internal;
                } else if (other.interfaceNames.contains(required)) {
mport com.github.dropguard.summer.core.Internal;
                    matches.add(other);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (!matches.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                deps.put(bean, matches);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Set<BeanDefinition> visited = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
        Set<BeanDefinition> inStack = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> order = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            dfs(bean, deps, visited, inStack, order);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return order;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void dfs(
mport com.github.dropguard.summer.core.Internal;
            BeanDefinition bean,
mport com.github.dropguard.summer.core.Internal;
            Map<BeanDefinition, Set<BeanDefinition>> deps,
mport com.github.dropguard.summer.core.Internal;
            Set<BeanDefinition> visited,
mport com.github.dropguard.summer.core.Internal;
            Set<BeanDefinition> inStack,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> order) {
mport com.github.dropguard.summer.core.Internal;
        if (visited.contains(bean)) return;
mport com.github.dropguard.summer.core.Internal;
        visited.add(bean);
mport com.github.dropguard.summer.core.Internal;
        inStack.add(bean);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        Set<BeanDefinition> beanDeps = deps.getOrDefault(bean, Set.of());
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition dep : beanDeps) {
mport com.github.dropguard.summer.core.Internal;
            if (!visited.contains(dep)) {
mport com.github.dropguard.summer.core.Internal;
                dfs(dep, deps, visited, inStack, order);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        inStack.remove(bean);
mport com.github.dropguard.summer.core.Internal;
        order.add(bean);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── @Replaces ─────────────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void resolveReplaces(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        // First pass: log method-level @Replaces from pre-populated field
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (!bean.isFactoryMethod()) continue;
mport com.github.dropguard.summer.core.Internal;
            if (bean.methodLevelReplaces != null) {
mport com.github.dropguard.summer.core.Internal;
                log.debug(
mport com.github.dropguard.summer.core.Internal;
                        "[Summer] Method-level @Replaces: {}.{} replaces {}",
mport com.github.dropguard.summer.core.Internal;
                        bean.configClassName,
mport com.github.dropguard.summer.core.Internal;
                        bean.producerMethodName,
mport com.github.dropguard.summer.core.Internal;
                        bean.methodLevelReplaces);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        // Second pass: collect all replaced beans
mport com.github.dropguard.summer.core.Internal;
        List<BeanDefinition> replaced = new ArrayList<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            // Class-level @Replaces
mport com.github.dropguard.summer.core.Internal;
            if (bean.replacesTargetClass != null) {
mport com.github.dropguard.summer.core.Internal;
                BeanDefinition target = findBeanByName(beans, bean.replacesTargetClass);
mport com.github.dropguard.summer.core.Internal;
                if (target == null)
mport com.github.dropguard.summer.core.Internal;
                    throw new NoSuchBeanException(
mport com.github.dropguard.summer.core.Internal;
                            "@Replaces target not found: " + bean.replacesTargetClass);
mport com.github.dropguard.summer.core.Internal;
                log.debug(
mport com.github.dropguard.summer.core.Internal;
                        "[Summer] Class-level @Replaces: {} replaces {}",
mport com.github.dropguard.summer.core.Internal;
                        bean.qualifiedName,
mport com.github.dropguard.summer.core.Internal;
                        bean.replacesTargetClass);
mport com.github.dropguard.summer.core.Internal;
                replaced.add(target);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            // Method-level @Replaces
mport com.github.dropguard.summer.core.Internal;
            if (bean.methodLevelReplaces != null) {
mport com.github.dropguard.summer.core.Internal;
                BeanDefinition target = findBeanByReturnType(beans, bean.methodLevelReplaces, bean);
mport com.github.dropguard.summer.core.Internal;
                if (target == null)
mport com.github.dropguard.summer.core.Internal;
                    throw new NoSuchBeanException(
mport com.github.dropguard.summer.core.Internal;
                            "@Replaces target not found: " + bean.methodLevelReplaces);
mport com.github.dropguard.summer.core.Internal;
                String beanDesc =
mport com.github.dropguard.summer.core.Internal;
                        bean.isFactoryMethod()
mport com.github.dropguard.summer.core.Internal;
                                ? bean.configClassName + "#" + bean.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                                : bean.qualifiedName;
mport com.github.dropguard.summer.core.Internal;
                String targetDesc =
mport com.github.dropguard.summer.core.Internal;
                        target.isFactoryMethod()
mport com.github.dropguard.summer.core.Internal;
                                ? target.configClassName + "#" + target.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                                : target.qualifiedName;
mport com.github.dropguard.summer.core.Internal;
                log.debug("[Summer] Method-level @Replaces: {} replaces {}", beanDesc, targetDesc);
mport com.github.dropguard.summer.core.Internal;
                replaced.add(target);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        log.debug("[Summer] Removing {} replaced beans", replaced.size());
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition r : replaced) {
mport com.github.dropguard.summer.core.Internal;
            String desc =
mport com.github.dropguard.summer.core.Internal;
                    r.isFactoryMethod()
mport com.github.dropguard.summer.core.Internal;
                            ? r.configClassName + "#" + r.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                            : r.qualifiedName;
mport com.github.dropguard.summer.core.Internal;
            log.debug("[Summer]   Removing: {} ({})", desc, r.getClass().getSimpleName());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        beans.removeAll(replaced);
mport com.github.dropguard.summer.core.Internal;
        // A @Bean product is owned by its @Configuration class. When that class is
mport com.github.dropguard.summer.core.Internal;
        // removed by @Replaces, the product is orphaned and must go too.
mport com.github.dropguard.summer.core.Internal;
        // Keyed by configClassName against the qualifiedNames of beans still
mport com.github.dropguard.summer.core.Internal;
        // present (the previous version matched qualifiedName against configClassName,
mport com.github.dropguard.summer.core.Internal;
        // conflating two distinct fields and wrongly purging valid @Bean products).
mport com.github.dropguard.summer.core.Internal;
        Set<String> liveNames =
mport com.github.dropguard.summer.core.Internal;
                beans.stream()
mport com.github.dropguard.summer.core.Internal;
                        .map(b -> b.qualifiedName)
mport com.github.dropguard.summer.core.Internal;
                        .collect(java.util.stream.Collectors.toSet());
mport com.github.dropguard.summer.core.Internal;
        beans.removeIf(p -> p.isFactoryMethod() && !liveNames.contains(p.configClassName));
mport com.github.dropguard.summer.core.Internal;
        log.debug("[Summer] Beans after resolveReplaces: {} remaining", beans.size());
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── @ConditionalOnBean ────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void resolveConditionalOnBean(
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans,
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> topoOrder,
mport com.github.dropguard.summer.core.Internal;
            Map<String, String> requiredTypes,
mport com.github.dropguard.summer.core.Internal;
            BeanDeployment moduleIndex) {
mport com.github.dropguard.summer.core.Internal;
        // Visibility is currently GLOBAL: a @ConditionalOnBean(X) on bean B is
mport com.github.dropguard.summer.core.Internal;
        // satisfied by any bean T (or interface it implements) in the candidate set,
mport com.github.dropguard.summer.core.Internal;
        // regardless of archive. The {@link BeanDefinition#archiveName} field
mport com.github.dropguard.summer.core.Internal;
        // and {@link BeanDeployment} archive API are in place as the boundary
mport com.github.dropguard.summer.core.Internal;
        // contract, but the hard archive-scoped boundary is intentionally NOT
mport com.github.dropguard.summer.core.Internal;
        // enforced yet: the framework relies on cross-archive @ConditionalOnBean
mport com.github.dropguard.summer.core.Internal;
        // (e.g. RowMapperConfiguration @ConditionalOnBean(JdbcTemplate),
mport com.github.dropguard.summer.core.Internal;
        // where JdbcTemplate is supplied by the application/test and lives in a
mport com.github.dropguard.summer.core.Internal;
        // different archive). A future explicit cross-archive contract
mport com.github.dropguard.summer.core.Internal;
        // (import/export or dependency-visible) will tighten this; until then the
mport com.github.dropguard.summer.core.Internal;
        // boundary stays global to avoid wrongly dropping framework beans.
mport com.github.dropguard.summer.core.Internal;
        Set<String> available = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            available.add(bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
            available.addAll(bean.interfaceNames);
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : topoOrder) {
mport com.github.dropguard.summer.core.Internal;
            if (!beans.contains(bean)) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            String required = requiredTypes.get(bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
            if (required == null) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            if (!available.contains(required)) {
mport com.github.dropguard.summer.core.Internal;
                available.remove(bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
                available.removeAll(bean.interfaceNames);
mport com.github.dropguard.summer.core.Internal;
                beans.remove(bean);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Helpers ───────────────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private BeanDefinition findBeanByName(List<BeanDefinition> beans, String name) {
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean.qualifiedName.equals(name)) return bean;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private BeanDefinition findBeanByReturnType(
mport com.github.dropguard.summer.core.Internal;
            List<BeanDefinition> beans, String returnType, BeanDefinition replacement) {
mport com.github.dropguard.summer.core.Internal;
        BeanDefinition found = null;
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean == replacement) continue;
mport com.github.dropguard.summer.core.Internal;
            if (bean.isFactoryMethod() && bean.qualifiedName.equals(returnType)) {
mport com.github.dropguard.summer.core.Internal;
                if (found != null) {
mport com.github.dropguard.summer.core.Internal;
                    throw new AmbiguousBeanException(
mport com.github.dropguard.summer.core.Internal;
                            "Ambiguous @Replaces: multiple @Bean methods return "
mport com.github.dropguard.summer.core.Internal;
                                    + returnType
mport com.github.dropguard.summer.core.Internal;
                                    + ": "
mport com.github.dropguard.summer.core.Internal;
                                    + found.configClassName
mport com.github.dropguard.summer.core.Internal;
                                    + "."
mport com.github.dropguard.summer.core.Internal;
                                    + found.producerMethodName
mport com.github.dropguard.summer.core.Internal;
                                    + " and "
mport com.github.dropguard.summer.core.Internal;
                                    + bean.configClassName
mport com.github.dropguard.summer.core.Internal;
                                    + "."
mport com.github.dropguard.summer.core.Internal;
                                    + bean.producerMethodName);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                found = bean;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return found;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
