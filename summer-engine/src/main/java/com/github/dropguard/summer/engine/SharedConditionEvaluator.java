package com.github.dropguard.summer.engine;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared condition evaluator used by both Runtime and AOT engines.
 *
 * <p>Candidate selection, in two stages:
 *
 * <ol>
 *   <li><b>Production layout</b>, computed exactly as production would, without mocks: evaluate
 *       {@code @ConditionalOnBean} in topological order (cascading; a conditional cycle fails
 *       fast), resolve {@code @Replaces} (class-level and method-level, purging any {@code @Bean}
 *       product whose {@code @Configuration} was withdrawn), and let surviving producers override
 *       their {@code @ConfigMapping} defaults
 *   <li><b>Mocks</b>: each {@code @Mock X} withdraws whatever holds X's position in that layout
 * </ol>
 *
 * <p>Contract: a mock never decides which beans exist, only which instance holds a position. So a
 * mock of a type the layout withdrew (its position is empty in production) fails the build, and a
 * mock of a type that was never discovered (a narrow test's collaborator) supplies that type, the
 * one case where a mock is visible to conditions.
 *
 * <p>Every withdrawn candidate is recorded with its reason ({@link #withdrawals()}): a one-line
 * summary is logged at info, and a later missing-bean failure names why the bean is gone.
 *
 * <p>Reads conditions and replaces from {@link BeanDefinition} fields populated during discovery —
 * no Jandex access at evaluation time.
 */
@Internal
public final class SharedConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SharedConditionEvaluator.class);

    /** Withdrawn candidate → why. */
    private final Map<BeanDefinition, String> withdrawals = new HashMap<>();

    /** The keys of {@link #withdrawals}, in withdrawal order. */
    private final List<BeanDefinition> withdrawalOrder = new ArrayList<>();

    /** Diagnostics on mocks that add a type the universe would not otherwise have. */
    private final List<MockNote> mockNotes = new ArrayList<>();

    /** Whether the deployment is a narrow test universe (explicit seed classes only). */
    private final boolean narrowUniverse;

    /** How loudly a {@link MockNote} is logged. */
    public enum Severity {
        DEBUG,
        INFO,
        WARN
    }

    /** One mock diagnostic, as logged. */
    public record MockNote(Severity severity, String message) {}

    /** An evaluator for a whole-universe deployment (production, or a whole-classpath test). */
    public SharedConditionEvaluator() {
        this(false);
    }

    /**
     * An evaluator for the given deployment shape.
     *
     * @param narrowUniverse whether the deployment is a narrow test universe; it sets how loudly
     *     the mock diagnostics ({@link #mockNotes()}) are logged
     */
    public SharedConditionEvaluator(boolean narrowUniverse) {
        this.narrowUniverse = narrowUniverse;
    }

    /**
     * Evaluates conditions and removes unsatisfied beans.
     *
     * @param beans bean list (mutated in place)
     */
    public void evaluate(List<BeanDefinition> beans) {
        evaluate(beans, Set.of());
    }

    /**
     * Lays out the candidates as production would, then applies the {@code @Mock}s declared on the
     * test (see the class contract).
     *
     * <p>A mocked occupant is a <b>discovery-stage type replacement</b> (Quarkus-style): the real
     * definition is taken out of the candidate set entirely, so it is never instantiated — and
     * because nothing references it, its dependency closure is also never instantiated. This runs
     * identically on both the Runtime and AOT engines (both funnel through this evaluator).
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
     * @throws BeanCreationException when a conditional cycle exists, or a mock's type was
     *     discovered but its position is empty in the production layout
     */
    public void evaluate(List<BeanDefinition> beans, Set<String> mockedTypes) {
        List<BeanDefinition> discovered = mockedTypes.isEmpty() ? List.of() : List.copyOf(beans);
        Set<String> supplied = typesWithoutCounterpart(discovered, mockedTypes);
        reportMocksWithoutCounterpart(supplied);
        List<BeanDefinition> conditionWithdrawn = resolveConditionalOnBean(beans, supplied);
        Map<BeanDefinition, BeanDefinition> replacedBy = resolveReplaces(beans, conditionWithdrawn);
        removeOverriddenConfigMappings(beans);
        applyMocks(beans, discovered, mockedTypes, replacedBy);
        logWithdrawalSummary();
    }

    /** The mock diagnostics this evaluation produced, in the order they were logged. */
    public List<MockNote> mockNotes() {
        return Collections.unmodifiableList(mockNotes);
    }

    /** Every candidate this evaluation withdrew, with the reason. */
    public Map<BeanDefinition, String> withdrawals() {
        return Collections.unmodifiableMap(withdrawals);
    }

    /**
     * The withdrawals indexed by every type key a withdrawn candidate would have supplied (its own
     * type and its interfaces), each entry rendered as {@code "candidate (reason)"} — the shape a
     * missing-bean diagnosis looks up by the missing type.
     */
    public Map<String, List<String>> withdrawalsByType() {
        Map<String, List<String>> byType = new HashMap<>();
        for (BeanDefinition bean : withdrawalOrder) {
            String line = describe(bean) + " (" + withdrawals.get(bean) + ")";
            byType.computeIfAbsent(bean.qualifiedName, k -> new ArrayList<>()).add(line);
            for (String iface : bean.interfaceNames) {
                byType.computeIfAbsent(iface, k -> new ArrayList<>()).add(line);
            }
        }
        return byType;
    }

    private void withdraw(BeanDefinition bean, String reason) {
        if (withdrawals.putIfAbsent(bean, reason) == null) {
            withdrawalOrder.add(bean);
        }
        log.debug("[Summer] Withdrawn: {} ({})", describe(bean), reason);
    }

    private void logWithdrawalSummary() {
        if (withdrawals.isEmpty()) {
            return;
        }
        log.info(
                "[Summer] Withdrew {} candidate(s): {}",
                withdrawals.size(),
                withdrawalOrder.stream()
                        .map(b -> describe(b) + " (" + withdrawals.get(b) + ")")
                        .collect(Collectors.joining("; ")));
    }

    // ── @Mock ─────────────────────────────────────────────────────

    /** Mocked types no discovered candidate supplies: the mock is their only source. */
    private static Set<String> typesWithoutCounterpart(
            List<BeanDefinition> discovered, Set<String> mockedTypes) {
        Set<String> supplied = new TreeSet<>();
        for (String type : mockedTypes) {
            if (discovered.stream().noneMatch(b -> suppliesType(b, type))) {
                supplied.add(type);
            }
        }
        return supplied;
    }

    /**
     * A mock whose type no discovered candidate supplied fills a position the universe does not
     * have. Legal: narrow universes mock collaborators instead of listing them, so there it is only
     * a debug note. In a whole universe it usually means the real bean was removed or renamed and
     * the mock is stale, so it warns.
     */
    private void reportMocksWithoutCounterpart(Set<String> supplied) {
        for (String type : supplied) {
            note(
                    narrowUniverse ? Severity.DEBUG : Severity.WARN,
                    "@Mock "
                            + type
                            + " has no discovered counterpart: the mock adds a bean this"
                            + " universe would not otherwise have"
                            + (narrowUniverse
                                    ? ""
                                    : " (stale mock? the real bean may have been removed or"
                                            + " renamed)"));
        }
    }

    private void note(Severity severity, String message) {
        mockNotes.add(new MockNote(severity, message));
        switch (severity) {
            case WARN -> log.warn("[Summer] {}", message);
            case INFO -> log.info("[Summer] {}", message);
            case DEBUG -> log.debug("[Summer] {}", message);
        }
    }

    /**
     * Withdraws, for every mocked type with a discovered counterpart, whatever holds that type's
     * position in the production layout: a surviving candidate of the type, or the surviving end of
     * the {@code @Replaces} chain that displaced one. The mock instance itself is supplied
     * separately at instance-build time.
     *
     * <p>When nothing holds the position (every candidate of the type was withdrawn by the layout),
     * production has no such bean, and a mock cannot add one: the build fails, naming why.
     */
    private void applyMocks(
            List<BeanDefinition> beans,
            List<BeanDefinition> discovered,
            Set<String> mockedTypes,
            Map<BeanDefinition, BeanDefinition> replacedBy) {
        if (mockedTypes.isEmpty()) {
            return;
        }
        Set<BeanDefinition> live = new HashSet<>(beans);
        Set<BeanDefinition> displaced = new HashSet<>();
        for (String type : new TreeSet<>(mockedTypes)) {
            List<BeanDefinition> candidates =
                    discovered.stream().filter(b -> suppliesType(b, type)).toList();
            if (candidates.isEmpty()) {
                continue; // supplied by the mock (typesWithoutCounterpart)
            }
            Set<BeanDefinition> holders = new LinkedHashSet<>();
            for (BeanDefinition candidate : candidates) {
                BeanDefinition holder = holderOf(candidate, live, replacedBy);
                if (holder != null) {
                    holders.add(holder);
                }
            }
            if (holders.isEmpty()) {
                throw new BeanCreationException(emptyPosition(type, candidates));
            }
            for (BeanDefinition holder : holders) {
                displaced.add(holder);
                withdraw(
                        holder,
                        candidates.contains(holder)
                                ? "replaced by its @Mock"
                                : "displaced by @Mock "
                                        + type
                                        + ": through @Replaces it holds that position; mock "
                                        + holder.qualifiedName
                                        + " itself, or inject an interface the mock implements");
            }
        }
        beans.removeAll(displaced);
        purgeOrphanedProducts(beans);
    }

    /**
     * The live candidate holding {@code candidate}'s position: itself if it survived, else the
     * surviving end of the {@code @Replaces} chain that displaced it, else {@code null}.
     */
    private static BeanDefinition holderOf(
            BeanDefinition candidate,
            Set<BeanDefinition> live,
            Map<BeanDefinition, BeanDefinition> replacedBy) {
        Set<BeanDefinition> seen = new HashSet<>();
        for (BeanDefinition at = candidate; at != null && seen.add(at); at = replacedBy.get(at)) {
            if (live.contains(at)) {
                return at;
            }
        }
        return null;
    }

    private String emptyPosition(String type, List<BeanDefinition> candidates) {
        String why =
                candidates.stream()
                        .map(c -> describe(c) + ": " + withdrawals.getOrDefault(c, "withdrawn"))
                        .collect(Collectors.joining("; "));
        return "@Mock "
                + type
                + " stands in for nothing: in production that position is empty ("
                + why
                + "). A mock replaces a bean; it cannot add one production withdraws. Satisfy"
                + " the condition in this test's universe, or drop the mock.";
    }

    /** Whether {@code bean} supplies {@code type}: its own type or one of its interfaces. */
    private static boolean suppliesType(BeanDefinition bean, String type) {
        return bean.qualifiedName.equals(type) || bean.interfaceNames.contains(type);
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
                    if (suppliesType(other, requiredType)) {
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
            // circular-dependency check instead of leaving the cycle undetected. The layout is
            // mock-free, so no mock can hide a cycle production would hit.
            throw new BeanCreationException(
                    "Conditional dependency cycle detected: "
                            + bean.qualifiedName
                            + " participates in a @ConditionalOnBean cycle, which production can"
                            + " never satisfy (a @Mock does not break it)");
        }
        if (visited.contains(bean)) return;
        visited.add(bean);
        inStack.add(bean);

        for (BeanDefinition dep : deps.getOrDefault(bean, Set.of())) {
            dfs(dep, deps, visited, inStack, order);
        }

        inStack.remove(bean);
        order.add(bean);
    }

    // ── @Replaces ─────────────────────────────────────────────────

    /**
     * Resolves {@code @Replaces}; returns every replaced target with the replacer displacing it.
     */
    private Map<BeanDefinition, BeanDefinition> resolveReplaces(
            List<BeanDefinition> beans, List<BeanDefinition> conditionWithdrawn) {
        Map<BeanDefinition, BeanDefinition> replacedBy = new HashMap<>();
        for (BeanDefinition bean : beans) {
            // Class-level @Replaces
            if (bean.replacesTargetClass != null) {
                BeanDefinition target = findBeanByName(beans, bean.replacesTargetClass);
                if (target == null) {
                    requireWithdrawn(conditionWithdrawn, bean.replacesTargetClass, false);
                } else {
                    recordReplacement(replacedBy, target, bean);
                }
            }
            // Method-level @Replaces
            if (bean.methodLevelReplaces != null) {
                BeanDefinition target = findBeanByReturnType(beans, bean.methodLevelReplaces, bean);
                if (target == null) {
                    // Last block of the loop body: skipping to the next bean is all that remains.
                    requireWithdrawn(conditionWithdrawn, bean.methodLevelReplaces, true);
                    continue;
                }
                recordReplacement(replacedBy, target, bean);
            }
        }

        beans.removeAll(replacedBy.keySet());
        purgeOrphanedProducts(beans);
        log.debug("[Summer] Beans after resolveReplaces: {} remaining", beans.size());
        return replacedBy;
    }

    /** The first replacer found for a target displaces it; later ones are ignored. */
    private void recordReplacement(
            Map<BeanDefinition, BeanDefinition> replacedBy,
            BeanDefinition target,
            BeanDefinition replacer) {
        if (replacedBy.putIfAbsent(target, replacer) == null) {
            withdraw(target, "replaced by @Replaces on " + describe(replacer));
        }
    }

    /**
     * A {@code @Bean} product is owned by its {@code @Configuration} class. When that class is
     * withdrawn (replaced, conditioned out, or mocked), the product is orphaned and must go too.
     * Keyed by configClassName against the qualifiedNames of beans still present.
     */
    private void purgeOrphanedProducts(List<BeanDefinition> beans) {
        Set<String> liveNames =
                beans.stream().map(b -> b.qualifiedName).collect(Collectors.toSet());
        for (var it = beans.iterator(); it.hasNext(); ) {
            BeanDefinition product = it.next();
            if (product.isFactoryMethod() && !liveNames.contains(product.configClassName)) {
                it.remove();
                withdraw(product, "its @Configuration " + product.configClassName + " is absent");
            }
        }
    }

    // ── @ConditionalOnBean ────────────────────────────────────────

    /**
     * Walks every {@code @ConditionalOnBean} once, in topological order, and withdraws the beans
     * whose requirement is not met. Returns the beans it withdrew.
     *
     * <p>Contract: conditions evaluate against the WHOLE candidate universe — the same single
     * visibility model as bean injection — while walking topological order, so cascading drops
     * work: if a provider was itself dropped, its dependents see it gone. Mocks play no part,
     * except that {@code supplied} (mocked types with no discovered counterpart) are permanently
     * available: there the mock is the type's only source.
     */
    private List<BeanDefinition> resolveConditionalOnBean(
            List<BeanDefinition> beans, Set<String> supplied) {
        Map<BeanDefinition, Set<String>> requiredTypes = collectConditionalRequirements(beans);
        List<BeanDefinition> topoOrder = buildTopologicalOrder(beans, requiredTypes);
        KeyLedger ledger = new KeyLedger(beans, supplied);
        List<BeanDefinition> dropped = new ArrayList<>();

        for (BeanDefinition bean : topoOrder) {
            Set<String> required = requiredTypes.get(bean);
            if (required == null) continue;

            List<String> missing = ledger.missing(required);
            if (!missing.isEmpty()) {
                beans.remove(bean);
                dropped.add(bean);
                withdraw(bean, unsatisfied(missing));
                ledger.revoke(bean);
            }
        }
        return dropped;
    }

    private static String unsatisfied(List<String> missing) {
        return "@ConditionalOnBean(" + String.join(", ", missing) + ") not satisfied";
    }

    /**
     * Which type keys are currently supplied. Interface keys are SHARED: several implementors
     * contribute the same interface name, so a withdrawn bean revokes a key only when it was the
     * last live contributor — unconditionally revoking its interfaceNames would strip a shared key
     * from survivors (dropping XmlCodec must not revoke Codec while JsonCodec still implements it)
     * and cascade false drops onto {@code @ConditionalOnBean(Codec)} dependents. {@code permanent}
     * keys (supplied by a mock with no discovered counterpart) are never revoked.
     */
    private static final class KeyLedger {
        private final Map<String, Set<BeanDefinition>> contributors = new HashMap<>();
        private final Set<String> available = new HashSet<>();
        private final Set<String> permanent;

        KeyLedger(List<BeanDefinition> beans, Set<String> permanent) {
            this.permanent = permanent;
            for (BeanDefinition bean : beans) {
                for (String key : keysOf(bean)) {
                    contributors.computeIfAbsent(key, k -> new HashSet<>()).add(bean);
                }
            }
            available.addAll(contributors.keySet());
            available.addAll(permanent);
        }

        /** The required keys not currently supplied, sorted (empty when all are met). */
        List<String> missing(Set<String> required) {
            List<String> missing = new ArrayList<>();
            for (String key : required) {
                if (!available.contains(key)) {
                    missing.add(key);
                }
            }
            Collections.sort(missing);
            return missing;
        }

        void revoke(BeanDefinition bean) {
            for (String key : keysOf(bean)) {
                Set<BeanDefinition> cs = contributors.get(key);
                if (cs == null) continue;
                cs.remove(bean);
                if (cs.isEmpty() && !permanent.contains(key)) {
                    available.remove(key);
                }
            }
        }

        private static List<String> keysOf(BeanDefinition bean) {
            List<String> keys = new ArrayList<>(bean.interfaceNames);
            keys.add(bean.qualifiedName);
            return keys;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** {@code Config#method} for a {@code @Bean} product, the class name otherwise. */
    static String describe(BeanDefinition bean) {
        return bean.isFactoryMethod()
                ? bean.configClassName + "#" + bean.producerMethodName
                : bean.qualifiedName;
    }

    /**
     * A {@code @Replaces} target missing from the live candidates is an error only when it was
     * never discovered (a typo, a missing module). A target a failed {@code @ConditionalOnBean}
     * already withdrew leaves nothing to replace: the replacer simply stands (Micronaut parity).
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
