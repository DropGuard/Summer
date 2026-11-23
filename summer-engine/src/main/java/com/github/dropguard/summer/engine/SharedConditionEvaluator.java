package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared condition evaluator used by both Runtime and AOT engines.
 *
 * <p>Candidate selection:
 *
 * <ol>
 *   <li>Collect {@code @ConditionalOnBean} requirements
 *   <li>Evaluate {@code @ConditionalOnBean} in topological order, remove unsatisfied beans
 *   <li>Resolve {@code @Replaces} (class-level and method-level), remove replaced beans and any
 *       {@code @Bean} product whose {@code @Configuration} was replaced
 *   <li>Let surviving producers override their matching {@code @ConfigMapping} defaults
 * </ol>
 *
 * <p>Reads conditions and replaces from {@link BeanDefinition} fields populated during discovery —
 * no Jandex access at evaluation time.
 */
@Internal
public final class SharedConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SharedConditionEvaluator.class);

    public SharedConditionEvaluator() {}

    /**
     * Evaluates conditions and removes unsatisfied beans.
     *
     * @param beans bean list (mutated in place)
     */
    public void evaluate(List<BeanDefinition> beans) {
        evaluate(beans, Set.of());
    }

    /**
     * Evaluates conditions, removes unsatisfied beans, and removes any bean whose type is mocked by
     * a {@code @Mock} declared on the test.
     *
     * <p>Mock removal is a <b>discovery-stage type replacement</b> (Quarkus-style): the real
     * implementation is taken out of the candidate set entirely, so it is never instantiated — and
     * because nothing references it, its dependency closure is also never instantiated. This runs
     * identically on both the Runtime and AOT engines (both funnel through this evaluator), which
     * fixes the prior divergence where the AOT path let a concrete-class {@code @Mock} lose to the
     * real bean via registration-order overwrite.
     *
     * <p>{@code mockedTypes} holds the declared {@code @Mock} parameter type names (not {@code
     * mock.getClass()}, which for a Mockito proxy differs from the target type). Matching is by
     * exact type name and implemented interfaces, so it works without loading classes — safe for
     * the AOT compile phase.
     *
     * <p>{@code @ConditionalOnBean} visibility is the whole candidate universe — identical to bean
     * injection's visibility. There is deliberately no per-archive scoping: one container, one
     * visibility model (see {@code resolveConditionalOnBean}).
     *
     * @param beans bean list (mutated in place)
     * @param mockedTypes type names declared as {@code @Mock} on the test constructor
     */
    public void evaluate(List<BeanDefinition> beans, Set<String> mockedTypes) {
        List<BeanDefinition> mocked = removeMockedBeans(beans, mockedTypes);
        Map<BeanDefinition, Set<String>> requiredTypes = collectConditionalRequirements(beans);
        List<BeanDefinition> topoOrder = buildTopologicalOrder(beans, requiredTypes);
        // Withdrawn = discovered, then removed by a mock or a failed condition. @Replaces treats
        // such a target as "nothing left to replace", not as a missing one.
        List<BeanDefinition> withdrawn = new ArrayList<>(mocked);
        withdrawn.addAll(
                resolveConditionalOnBean(
                        beans, topoOrder, requiredTypes, mockSuppliedKeys(mockedTypes, mocked)));
        resolveReplaces(beans, withdrawn);
        removeOverriddenConfigMappings(beans);
    }

    private void removeOverriddenConfigMappings(List<BeanDefinition> beans) {
        Set<String> producedTypes = new HashSet<>();
        for (BeanDefinition bean : beans) {
            if (bean.isFactoryMethod()) {
                producedTypes.add(bean.qualifiedName);
                producedTypes.addAll(bean.interfaceNames);
            }
        }
        beans.removeIf(
                bean ->
                        bean instanceof ConfigPropertiesBean
                                && producedTypes.contains(bean.qualifiedName));
    }

    /**
     * Removes beans whose type (or any implemented interface) is among the mocked types, plus every
     * {@code @Replaces} replacer of a removed bean (transitively). The mock instance itself is
     * supplied separately at instance-build time; removing the real definitions ensures they are
     * never instantiated.
     *
     * <p>Rule: a mock takes over the position its target holds <i>in production</i>. If a replacer
     * would hold that position, the replacer is what the mock displaces — so class-level and
     * method-level replacers behave alike (only the mock remains).
     */
    private List<BeanDefinition> removeMockedBeans(
            List<BeanDefinition> beans, Set<String> mockedTypes) {
        List<BeanDefinition> removed = new ArrayList<>();
        if (mockedTypes.isEmpty()) {
            return removed;
        }
        for (var it = beans.iterator(); it.hasNext(); ) {
            BeanDefinition bean = it.next();
            if (isMocked(bean, mockedTypes)) {
                removed.add(bean);
                it.remove();
            }
        }
        // A mock takes over the position its target holds in production. That position belongs
        // to the whole @Replaces chain the target sits in: mocking the replaced bean displaces
        // its replacer (the production occupant), and mocking the replacer leaves its target
        // displaced as in production. Walk the chain both ways until it is fully withdrawn.
        boolean grew = !removed.isEmpty();
        while (grew) {
            grew = false;
            for (var it = beans.iterator(); it.hasNext(); ) {
                BeanDefinition bean = it.next();
                if (sharesReplacesChain(bean, removed)) {
                    log.debug(
                            "[Summer] {} withdrawn: it shares a @Replaces chain with a mocked"
                                    + " bean",
                            bean.qualifiedName);
                    removed.add(bean);
                    it.remove();
                    grew = true;
                }
            }
        }
        return removed;
    }

    /**
     * Whether {@code bean} replaces, or is replaced by, one of {@code withdrawn} via
     * {@code @Replaces}.
     */
    private static boolean sharesReplacesChain(
            BeanDefinition bean, List<BeanDefinition> withdrawn) {
        for (BeanDefinition w : withdrawn) {
            if (replaces(bean, w) || replaces(w, bean)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code replacer} declares a {@code @Replaces} that targets {@code target}. */
    private static boolean replaces(BeanDefinition replacer, BeanDefinition target) {
        if (replacer == target) {
            return false;
        }
        if (target.qualifiedName.equals(replacer.replacesTargetClass)) {
            return true;
        }
        return target.isFactoryMethod()
                && target.qualifiedName.equals(replacer.methodLevelReplaces);
    }

    private static boolean isMocked(BeanDefinition bean, Set<String> mockedTypes) {
        if (mockedTypes.contains(bean.qualifiedName)) {
            return true;
        }
        for (String iface : bean.interfaceNames) {
            if (mockedTypes.contains(iface)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Type keys a mock supplies: each mocked type, plus the interfaces of a mocked concrete type. A
     * mock is always registered, so these keys satisfy {@code @ConditionalOnBean} exactly as the
     * real bean would have — mocking a bean must not silently shrink the rest of the universe.
     */
    private static Set<String> mockSuppliedKeys(
            Set<String> mockedTypes, List<BeanDefinition> mocked) {
        Set<String> keys = new HashSet<>(mockedTypes);
        for (BeanDefinition bean : mocked) {
            if (mockedTypes.contains(bean.qualifiedName)) {
                keys.addAll(bean.interfaceNames);
            }
        }
        return keys;
    }

    // ── Collect @ConditionalOnBean requirements ───────────────────

    private Map<BeanDefinition, Set<String>> collectConditionalRequirements(
            List<BeanDefinition> beans) {
        Map<BeanDefinition, Set<String>> requiredTypes = new HashMap<>();
        for (BeanDefinition bean : beans) {
            // AND semantics (Quarkus/Spring parity): a @Bean product's class-level condition and
            // its producer method's condition are BOTH checked — each declared prerequisite is
            // accumulated, neither is dropped by a single-slot overwrite.
            Set<String> required = new HashSet<>();
            if (bean.conditionalOnBeanType != null) {
                required.add(bean.conditionalOnBeanType);
            }
            if (bean.methodConditionalOnBeanType != null) {
                required.add(bean.methodConditionalOnBeanType);
            }
            if (!required.isEmpty()) {
                requiredTypes.put(bean, required);
            }
        }
        return requiredTypes;
    }

    // ── Topological sort ──────────────────────────────────────────

    private List<BeanDefinition> buildTopologicalOrder(
            List<BeanDefinition> beans, Map<BeanDefinition, Set<String>> requiredTypes) {
        Map<BeanDefinition, Set<BeanDefinition>> deps = new HashMap<>();
        for (BeanDefinition bean : beans) {
            Set<String> required = requiredTypes.get(bean);
            if (required == null) continue;

            Set<BeanDefinition> matches = new HashSet<>();
            for (String requiredType : required) {
                for (BeanDefinition other : beans) {
                    if (other.qualifiedName.equals(requiredType)) {
                        matches.add(other);
                    } else if (other.interfaceNames.contains(requiredType)) {
                        matches.add(other);
                    }
                }
            }
            if (!matches.isEmpty()) {
                deps.put(bean, matches);
            }
        }

        Set<BeanDefinition> visited = new HashSet<>();
        Set<BeanDefinition> inStack = new HashSet<>();
        List<BeanDefinition> order = new ArrayList<>();
        for (BeanDefinition bean : beans) {
            dfs(bean, deps, visited, inStack, order);
        }
        return order;
    }

    private void dfs(
            BeanDefinition bean,
            Map<BeanDefinition, Set<BeanDefinition>> deps,
            Set<BeanDefinition> visited,
            Set<BeanDefinition> inStack,
            List<BeanDefinition> order) {
        if (inStack.contains(bean)) {
            // A conditional cycle (A @ConditionalOnBean B, B @ConditionalOnBean A) can never
            // satisfy either side — it would silently drop both beans. Fail fast like the regular
            // circular-dependency check instead of leaving the cycle undetected.
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
                    "Conditional dependency cycle detected: "
                            + bean.qualifiedName
                            + " participates in a @ConditionalOnBean cycle");
        }
        if (visited.contains(bean)) return;
        visited.add(bean);
        inStack.add(bean);

        Set<BeanDefinition> beanDeps = deps.getOrDefault(bean, Set.of());
        for (BeanDefinition dep : beanDeps) {
            dfs(dep, deps, visited, inStack, order);
        }

        inStack.remove(bean);
        order.add(bean);
    }

    // ── @Replaces ─────────────────────────────────────────────────

    private void resolveReplaces(List<BeanDefinition> beans, List<BeanDefinition> withdrawn) {
        // First pass: log method-level @Replaces from pre-populated field
        for (BeanDefinition bean : beans) {
            if (!bean.isFactoryMethod()) continue;
            if (bean.methodLevelReplaces != null) {
                log.debug(
                        "[Summer] Method-level @Replaces: {}.{} replaces {}",
                        bean.configClassName,
                        bean.producerMethodName,
                        bean.methodLevelReplaces);
            }
        }

        // Second pass: collect all replaced beans
        List<BeanDefinition> replaced = new ArrayList<>();
        for (BeanDefinition bean : beans) {
            // Class-level @Replaces
            if (bean.replacesTargetClass != null) {
                BeanDefinition target = findBeanByName(beans, bean.replacesTargetClass);
                if (target == null) {
                    requireWithdrawn(withdrawn, bean.replacesTargetClass, false);
                } else {
                    log.debug(
                            "[Summer] Class-level @Replaces: {} replaces {}",
                            bean.qualifiedName,
                            bean.replacesTargetClass);
                    replaced.add(target);
                }
            }
            // Method-level @Replaces
            if (bean.methodLevelReplaces != null) {
                BeanDefinition target = findBeanByReturnType(beans, bean.methodLevelReplaces, bean);
                if (target == null) {
                    // Last block of the loop body: skipping to the next bean is all that remains.
                    requireWithdrawn(withdrawn, bean.methodLevelReplaces, true);
                    continue;
                }
                String beanDesc =
                        bean.isFactoryMethod()
                                ? bean.configClassName + "#" + bean.producerMethodName
                                : bean.qualifiedName;
                String targetDesc =
                        target.isFactoryMethod()
                                ? target.configClassName + "#" + target.producerMethodName
                                : target.qualifiedName;
                log.debug("[Summer] Method-level @Replaces: {} replaces {}", beanDesc, targetDesc);
                replaced.add(target);
            }
        }

        log.debug("[Summer] Removing {} replaced beans", replaced.size());
        for (BeanDefinition r : replaced) {
            String desc =
                    r.isFactoryMethod()
                            ? r.configClassName + "#" + r.producerMethodName
                            : r.qualifiedName;
            log.debug("[Summer]   Removing: {} ({})", desc, r.getClass().getSimpleName());
        }
        beans.removeAll(replaced);
        // A @Bean product is owned by its @Configuration class. When that class is
        // removed by @Replaces, the product is orphaned and must go too.
        // Keyed by configClassName against the qualifiedNames of beans still
        // present (the previous version matched qualifiedName against configClassName,
        // conflating two distinct fields and wrongly purging valid @Bean products).
        Set<String> liveNames =
                beans.stream()
                        .map(b -> b.qualifiedName)
                        .collect(java.util.stream.Collectors.toSet());
        beans.removeIf(p -> p.isFactoryMethod() && !liveNames.contains(p.configClassName));
        log.debug("[Summer] Beans after resolveReplaces: {} remaining", beans.size());
    }

    // ── @ConditionalOnBean ────────────────────────────────────────

    private List<BeanDefinition> resolveConditionalOnBean(
            List<BeanDefinition> beans,
            List<BeanDefinition> topoOrder,
            Map<BeanDefinition, Set<String>> requiredTypes,
            Set<String> mockSupplied) {
        // Contract: conditions evaluate against the WHOLE candidate universe — the same single
        // visibility model as bean injection. Requirements are satisfied against the live bean
        // list while walking topological order, so cascading drops work: if a provider was
        // itself dropped, its dependents see it gone.
        //
        // Interface keys are SHARED: several implementors contribute the same interface name.
        // A dropped bean therefore revokes a key only when it was the last live contributor —
        // unconditionally revoking its interfaceNames would strip a shared key from survivors
        // (dropping XmlCodec must not revoke Codec while JsonCodec still implements it) and
        // cascade false drops onto @ConditionalOnBean(Codec) dependents.
        Map<String, Set<BeanDefinition>> contributors = new HashMap<>();
        for (BeanDefinition bean : beans) {
            contributors.computeIfAbsent(bean.qualifiedName, k -> new HashSet<>()).add(bean);
            for (String iface : bean.interfaceNames) {
                contributors.computeIfAbsent(iface, k -> new HashSet<>()).add(bean);
            }
        }
        Set<String> available = new HashSet<>(contributors.keySet());
        // Mock-supplied keys are permanent: no dropped real bean can revoke them.
        available.addAll(mockSupplied);
        List<BeanDefinition> dropped = new ArrayList<>();

        for (BeanDefinition bean : topoOrder) {
            if (!beans.contains(bean)) continue;

            Set<String> required = requiredTypes.get(bean);
            if (required == null) continue;

            boolean allPresent = true;
            for (String requiredType : required) {
                if (!available.contains(requiredType)) {
                    allPresent = false;
                    break;
                }
            }
            if (!allPresent) {
                beans.remove(bean);
                dropped.add(bean);
                List<String> contributed = new ArrayList<>(bean.interfaceNames);
                contributed.add(bean.qualifiedName);
                for (String key : contributed) {
                    Set<BeanDefinition> cs = contributors.get(key);
                    if (cs == null) continue;
                    cs.remove(bean);
                    if (cs.isEmpty() && !mockSupplied.contains(key)) {
                        available.remove(key);
                    }
                }
            }
        }
        return dropped;
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * A {@code @Replaces} target missing from the live candidates is an error only when it was
     * never discovered (a typo, a missing module). A target a {@code @Mock} or a failed
     * {@code @ConditionalOnBean} already withdrew leaves nothing to replace: the replacer simply
     * stands (Micronaut parity).
     */
    private void requireWithdrawn(
            List<BeanDefinition> withdrawn, String target, boolean producersOnly) {
        boolean found =
                withdrawn.stream()
                        .anyMatch(
                                b ->
                                        b.qualifiedName.equals(target)
                                                && (!producersOnly || b.isFactoryMethod()));
        if (!found) {
            throw new NoSuchBeanException("@Replaces target not found: " + target);
        }
        log.debug("[Summer] @Replaces target {} already withdrawn; nothing to replace", target);
    }

    private BeanDefinition findBeanByName(List<BeanDefinition> beans, String name) {
        for (BeanDefinition bean : beans) {
            if (bean.qualifiedName.equals(name)) return bean;
        }
        return null;
    }

    private BeanDefinition findBeanByReturnType(
            List<BeanDefinition> beans, String returnType, BeanDefinition replacement) {
        BeanDefinition found = null;
        for (BeanDefinition bean : beans) {
            if (bean == replacement) continue;
            if (bean.isFactoryMethod() && bean.qualifiedName.equals(returnType)) {
                if (found != null) {
                    throw new AmbiguousBeanException(
                            "Ambiguous @Replaces: multiple @Bean methods return "
                                    + returnType
                                    + ": "
                                    + found.configClassName
                                    + "."
                                    + found.producerMethodName
                                    + " and "
                                    + bean.configClassName
                                    + "."
                                    + bean.producerMethodName);
                }
                found = bean;
            }
        }
        return found;
    }
}
