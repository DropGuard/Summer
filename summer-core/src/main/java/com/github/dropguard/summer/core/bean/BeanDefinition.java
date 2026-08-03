package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.Internal;
import java.util.ArrayList;
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
     * Method-level interceptor binding annotations.
     *
     * <p>Key = method name, value = binding annotation qualified names. Key {@code ""} =
     * class-level bindings. Empty map = no AOP bindings.
     */
    public Map<String, Set<String>> methodBindingAnnotations = Map.of();

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

    // ── Phase 3: Resolution outputs ───────────────────────────────────

    /**
     * Discovery/enrichment-phase helper: append one injection parameter with no resolved
     * dependencies yet (the resolver fills {@code resolved} later). For a {@code List<T>}
     * parameter, {@code typeName} carries its generic argument (e.g. {@code
     * "java.util.List<com.github.dropguard.summer.aot.testfixtures.Foo>"}) so the element type
     * stays derivable — no separate field or flag.
     */
    public void addParameter(String typeName) {
        parameters.add(new InjectionParameter(typeName, new ArrayList<>()));
    }

    /** The @Configuration bean for this @Bean product. */
    public BeanDefinition configBeanDefinition;

    /** AOP interceptors matched to this bean. */
    public final List<BeanDefinition> interceptors = new ArrayList<>();

    /** Whether the bean implements AutoCloseable. */
    public boolean isAutoCloseable;

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
}
