package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bean definition — the single source of metadata for every bean in the container.
 *
 * <p><b>Phase 1 (identity):</b> Qualified &amp; simple name, set in constructor — never changes.
 *
 * <p><b>Phase 1b (discovery / enrichment):</b> Constructor params, routes, interfaces, AOP
 * bindings, condition/replaces metadata. Populated once during the discovery+enrichment pipeline,
 * <em>never re-derived</em> from Jandex or reflection afterward.
 *
 * <p><b>Phase 3 (resolution):</b> the resolver fills each {@link #parameters} entry's {@code
 * resolved} list (plus {@link #configBeanDefinition}, {@link #interceptors}). Set by the dependency
 * resolver after condition evaluation.
 *
 * <p>{@link ConfigPropertiesBean} extends this for YAML-bound configuration beans.
 */
@Internal
public sealed class BeanDefinition permits ConfigPropertiesBean {

    // ── Phase 1: Identity (immutable) ─────────────────────────────────

    /** Fully qualified class name. */
    public final String qualifiedName;

    /** Simple class name (no package). */
    public final String simpleName;

    /**
     * Name of the archive (immutable discovery unit) this bean belongs to. Populated during
     * discovery from {@link BeanDeployment#archiveOf(String)}. Drives {@code @ConditionalOnBean}
     * visibility: a condition is satisfied only by beans in the same archive. Null until discovery
     * assigns it.
     */
    public String archiveName;

    /** Java identifier for AOT code gen. Mutable for dedup. */
    public String variableName;

    /**
     * {@code @Order} value, resolved at discovery time. Mirrors {@code BeanContainer.orderOf}
     * semantics (class annotation wins, else first interface annotation, else MAX_VALUE) so the AOT
     * engine can sort {@code List<T>} injection slices the same way the runtime sorts {@code
     * getBeans} by instance order.
     */
    public int order = Integer.MAX_VALUE;

    // ── Phase 1b: Discovery / enrichment fields ───────────────────────

    /** Routes (for @RestController beans). Enrichment populates. */
    public final List<RouteInfo> routes = new ArrayList<>();

    /**
     * Injection parameters, in declaration order — one entry per constructor / {@code @Bean} method
     * parameter. Each entry is a self-contained {@link InjectionParameter} (its type + the
     * dependencies resolved for it). Replaces the previously shredded {@code constructorParamTypes}
     * / {@code producerParamTypes} / {@code listElementTypes} + flat {@code resolvedDependencies} —
     * position is the list index, no parallel collection to keep in sync, and consumers read
     * parameters directly instead of rebuilding the structure with cursors or reflection.
     */
    public final List<InjectionParameter> parameters = new ArrayList<>();

    /** Implemented interface names (includes transitive). Populated during discovery. */
    public final List<String> interfaceNames = new ArrayList<>();

    /**
     * AOP interceptor binding annotation qualified names. Populated during enrichment. Empty set =
     * no AOP bindings.
     */
    public Set<String> interceptorBindingAnnotations = Set.of();

    /**
     * Interceptor bindings for this bean, split into class-level (apply to every method) and
     * per-method (overload-exact {@link #methodBindingKey}). Both engines resolve a method's
     * effective binding set through {@link MethodBindings#bindingsFor}, so the
     * class-level/method-level union rule lives in one place. {@link MethodBindings#EMPTY} = no AOP
     * bindings.
     */
    public MethodBindings methodBindings = MethodBindings.EMPTY;

    /**
     * Overload-exact key for the per-method slot of {@link #methodBindings}: method name plus its
     * parameter type names. Keying by NAME alone made overloaded interface methods share whichever
     * overload discovery saw — {@code transfer(Order)} annotated but {@code transfer(User)} not
     * would intercept both. Class-level bindings live in a separate slot, so they never enter this
     * keyspace.
     *
     * <p>Both engines build it, and the renderings coincide for every expressible parameter shape:
     * Runtime derives each parameter from the erasure ({@code Class#getName()}), enrichment/AOT
     * from Jandex {@code Type#name()} — a type variable's erasure IS its leftmost bound (JLS 4.6),
     * which is exactly what Jandex {@code TypeVariable#name()} returns ({@code bounds[0]}).
     * Parameterized types render as the raw class name ({@code List<T>} &rarr; {@code
     * java.util.List}), primitives as {@code int}, arrays — of variables included, any dimension —
     * as {@code [L...;}/{@code [I}. Pinned dual-engine by GenericBoundTaggedServiceTest.
     *
     * <p>One consequence of keys being read back under the INTERFACE method's rendering: a binding
     * recorded from an impl method whose parameter types are narrowed relative to the interface's
     * ({@code save(BigDecimal)} implementing {@code save(T extends Number)}) matches no key and is
     * consistently not intercepted on either engine. Declare method bindings on the interface (or
     * on an impl whose declared signature matches the interface's bound-first rendering).
     */
    public static String methodBindingKey(String methodName, List<String> parameterTypeNames) {
        return methodName + "(" + String.join(",", parameterTypeNames) + ")";
    }

    /**
     * Non-null only for engine-provided (synthetic) beans: the pre-built instance to register
     * directly, instead of instantiating from a class. Mirrors Quarkus' synthetic bean instance.
     * Null for all scanned beans. Consumed by the runtime engine, which registers this object
     * as-is.
     */
    public Object syntheticInstance = null;

    /**
     * AOT construction expression for a synthetic bean — the Java source a code generator emits
     * verbatim to obtain the synthetic instance at container build time. Supplied at the definition
     * site (where the synthetic bean is declared), so the code generator stays ignorant of each
     * synthetic type's construction. Null for scanned beans and for synthetic beans with no AOT
     * form (the runtime engine ignores it and registers {@link #syntheticInstance} instead).
     */
    public String aotInstanceExpression = null;

    /**
     * @ExceptionHandler methods discovered on this bean. Populated during enrichment.
     */
    public final List<ExceptionHandlerEntry> exceptionHandlerMethods = new ArrayList<>();

    // ── Phase 1c: Annotation metadata (set during discovery) ──────────

    /** Class-level @Replaces target (null when absent). */
    public String replacesTargetClass;

    /** Class-level @ConditionalOnBean target (null when absent). */
    public String conditionalOnBeanType;

    /**
     * @Configuration class name (null = constructor-created).
     */
    public String configClassName;

    /**
     * @Bean method name (null = constructor-created).
     */
    public String producerMethodName;

    /** Whether this bean is an @Interceptor role marker. */
    public boolean isInterceptor;

    /** Method-level @Replaces return type (null when absent). */
    public String methodLevelReplaces;

    /** Method-level @ConditionalOnBean target (null when absent). */
    public String methodConditionalOnBeanType;

    /**
     * Name of the {@code @PreDestroy} lifecycle method (null when absent). Populated during
     * enrichment, following CDI semantics: the most specific declaration in the class hierarchy
     * wins, and {@code @Bean} products never receive it (the producer owns its product's teardown).
     * Runs at container shutdown on the raw instance, before {@code AutoCloseable close()}.
     */
    public String preDestroyMethod;

    // ── Phase 3: Resolution outputs ───────────────────────────────────

    /**
     * Discovery/enrichment-phase helper: append one injection parameter with no resolved
     * dependencies yet (the resolver fills {@code resolved} later). For a {@code List<T>}
     * parameter, {@code typeName} carries its generic argument (e.g. {@code
     * "java.util.List<com.example.Foo>"}) so the element type stays derivable — no separate field
     * or flag.
     */
    public void addParameter(String typeName) {
        parameters.add(new InjectionParameter(typeName, new ArrayList<>()));
    }

    /** The @Configuration bean for this @Bean product. */
    public BeanDefinition configBeanDefinition;

    /** AOP interceptors matched to this bean. */
    public final List<BeanDefinition> interceptors = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────

    public BeanDefinition(String qualifiedName, String simpleName) {
        this.qualifiedName = qualifiedName;
        this.simpleName = simpleName;
        this.variableName = toVariableName(simpleName);
    }

    /** Returns {@code true} if this bean is produced by a {@code @Bean} factory method. */
    public boolean isFactoryMethod() {
        return configClassName != null;
    }

    /**
     * Returns {@code true} if this bean needs an AOP proxy. Derived from {@link
     * #interceptorBindingAnnotations} and {@link #isInterceptor} — no separate field.
     */
    public boolean needsProxy() {
        return !interceptorBindingAnnotations.isEmpty() && !isInterceptor;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + qualifiedName;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String toVariableName(String simpleName) {
        if (simpleName.isEmpty()) return "bean";
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    // ── Nested types ──────────────────────────────────────────────────

    /**
     * Metadata for an {@code @ExceptionHandler} method.
     *
     * @param methodName the method name
     * @param exceptionClass the handled exception class name
     * @param parameterCount total method parameters
     */
    public record ExceptionHandlerEntry(
            String methodName, String exceptionClass, int parameterCount) {}

    /**
     * A bean's interceptor bindings, modelled as two explicit slots instead of a map with a magic
     * class-level key: {@code classLevel} applies to every method, {@code perMethod} is keyed by
     * {@link #methodBindingKey}. The effective binding set for a method is the UNION of the two
     * (the CDI interceptor-binding convention); {@link #bindingsFor} is the single place that rule
     * lives, so the Runtime and AOT engines resolve bindings identically by construction.
     */
    public static final class MethodBindings {

        public static final MethodBindings EMPTY = new MethodBindings(Set.of(), Map.of());

        private final Set<String> classLevel;
        private final Map<String, Set<String>> perMethod;

        public MethodBindings(Set<String> classLevel, Map<String, Set<String>> perMethod) {
            this.classLevel = Set.copyOf(classLevel);
            Map<String, Set<String>> copy = new HashMap<>();
            perMethod.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
            this.perMethod = Map.copyOf(copy);
        }

        /** No class-level and no per-method bindings. */
        public boolean isEmpty() {
            return classLevel.isEmpty() && perMethod.isEmpty();
        }

        /** Whether a method is intercepted: a class-level binding binds every method. */
        public boolean isBound(String methodKey) {
            return !classLevel.isEmpty() || perMethod.containsKey(methodKey);
        }

        /**
         * The effective binding names for a method: class-level unioned with its method-level set.
         */
        public Set<String> bindingsFor(String methodKey) {
            Set<String> methodLevel = perMethod.get(methodKey);
            if (classLevel.isEmpty()) {
                return methodLevel == null ? Set.of() : methodLevel;
            }
            Set<String> union = new HashSet<>(classLevel);
            if (methodLevel != null) {
                union.addAll(methodLevel);
            }
            return union;
        }
    }
}
