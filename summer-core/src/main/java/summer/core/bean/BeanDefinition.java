package summer.core.bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bean definition — the single source of metadata for every bean in the
 * container.
 *
 * <p>
 * <b>Phase 1 (identity):</b> Qualified &amp; simple name, set in constructor —
 * never changes.
 * </p>
 *
 * <p>
 * <b>Phase 1b (discovery / enrichment):</b> Constructor params, routes,
 * interfaces, AOP bindings, condition/replaces metadata. Populated once during
 * the discovery+enrichment pipeline, <em>never re-derived</em> from Jandex or
 * reflection afterward.
 * </p>
 *
 * <p>
 * <b>Phase 3 (resolution):</b> {@link #resolvedDependencies},
 * {@link #configBeanDefinition}, {@link #interceptors}. Set by the dependency
 * resolver after condition evaluation.
 * </p>
 *
 * <p>
 * {@link ConfigPropertiesBean} extends this for YAML-bound configuration beans.
 * </p>
 */
public sealed class BeanDefinition permits ConfigPropertiesBean {

	// ── Phase 1: Identity (immutable) ─────────────────────────────────

	/** Fully qualified class name. */
	public final String qualifiedName;

	/** Simple class name (no package). */
	public final String simpleName;

	/** Java identifier for AOT code gen. Mutable for dedup. */
	public String variableName;

	// ── Phase 1b: Discovery / enrichment fields ───────────────────────

	/** Routes (for @RestController beans). Enrichment populates. */
	public final List<RouteInfo> routes = new ArrayList<>();

	/** Constructor parameter type names (@Component path). */
	public final List<String> constructorParamTypes = new ArrayList<>();

	/** Constructor/@Bean index → generic List&lt;T&gt; element type name. */
	public final Map<Integer, String> listElementTypes = new HashMap<>();

	/** @Bean method parameter type names. */
	public final List<String> producerParamTypes = new ArrayList<>();

	/**
	 * Implemented interface names (includes transitive). Populated during
	 * discovery.
	 */
	public final List<String> interfaceNames = new ArrayList<>();

	/**
	 * AOP interceptor binding annotation qualified names. Populated during
	 * enrichment. Empty set = no AOP bindings.
	 */
	public Set<String> interceptorBindingAnnotations = Set.of();

	/**
	 * Method-level interceptor binding annotations.
	 *
	 * Key = method name, value = binding annotation qualified names. Key {@code ""}
	 * = class-level bindings. Empty map = no AOP bindings.
	 */
	public Map<String, Set<String>> methodBindingAnnotations = Map.of();

	/**
	 * @ExceptionHandler methods discovered on this bean. Populated during
	 *                   enrichment.
	 */
	public final List<ExceptionHandlerEntry> exceptionHandlerMethods = new ArrayList<>();

	// ── Phase 1c: Annotation metadata (set during discovery) ──────────

	/** Class-level @Replaces target (null when absent). */
	public String replacesTargetClass;

	/** Class-level @ConditionalOnBean target (null when absent). */
	public String conditionalOnBeanType;

	/** @Configuration class name (null = constructor-created). */
	public String configClassName;

	/** @Bean method name (null = constructor-created). */
	public String producerMethodName;

	/** Whether this bean is an @Interceptor role marker. */
	public boolean isInterceptor;

	/** Method-level @Replaces return type (null when absent). */
	public String methodLevelReplaces;

	/** Method-level @ConditionalOnBean target (null when absent). */
	public String methodConditionalOnBeanType;

	// ── Phase 3: Resolution outputs ───────────────────────────────────

	/** Resolved dependency edges. Populated by dependency resolver. */
	public final List<BeanDefinition> resolvedDependencies = new ArrayList<>();

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

	/**
	 * Returns {@code true} if this bean is produced by a {@code @Bean} factory
	 * method.
	 */
	public boolean isFactoryMethod() {
		return configClassName != null;
	}

	/**
	 * Returns {@code true} if this bean needs an AOP proxy. Derived from
	 * {@link #interceptorBindingAnnotations} and {@link #isInterceptor} — no
	 * separate field.
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
		if (simpleName.isEmpty())
			return "bean";
		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
	}

	// ── Nested types ──────────────────────────────────────────────────

	/**
	 * Metadata for an {@code @ExceptionHandler} method.
	 *
	 * @param methodName
	 *            the method name
	 * @param exceptionClass
	 *            the handled exception class name
	 * @param parameterCount
	 *            total method parameters
	 */
	public record ExceptionHandlerEntry(String methodName, String exceptionClass, int parameterCount) {
	}
}
