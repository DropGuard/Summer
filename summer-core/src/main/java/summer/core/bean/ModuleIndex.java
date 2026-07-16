package summer.core.bean;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.IndexView;

/**
 * Associates classes with their originating module and retains each module's
 * own {@link IndexView} separately — the index is <b>not</b> collapsed into a
 * single merged view. Bean discovery reads only the indexes of modules whose
 * classes are within the requested {@link Scope}, which means the engine
 * naturally honours module boundaries without post-hoc filtering.
 *
 * <p>
 * Built by {@code JandexIndexLoader} from each {@code META-INF/jandex.idx}
 * location. Enables module-scoped bean discovery — a test can request "only
 * beans from summer-twitter and its dependencies" rather than the full merged
 * index.
 * </p>
 */
public final class ModuleIndex {

	private final IndexView mergedIndex;
	private final Map<String, String> classToModule;
	private final Set<String> allTypeNames;
	private final Map<String, IndexView> moduleIndexes;

	/**
	 * @param mergedIndex
	 *            fallback merged view for cross-module annotation resolution
	 * @param classToModule
	 *            class name → module name
	 * @param moduleIndexes
	 *            module name → its raw IndexView (not merged)
	 */
	public ModuleIndex(IndexView mergedIndex, Map<String, String> classToModule, Map<String, IndexView> moduleIndexes) {
		this.mergedIndex = mergedIndex;
		this.classToModule = classToModule;
		this.allTypeNames = Collections.unmodifiableSet(classToModule.keySet());
		this.moduleIndexes = Collections.unmodifiableMap(moduleIndexes);
	}

	/** The merged fallback index (all classes from all modules). */
	public IndexView index() {
		return mergedIndex;
	}

	/**
	 * Returns the {@link IndexView} for a single module, or the merged fallback if
	 * no per-module index was recorded.
	 */
	public IndexView moduleIndex(String moduleName) {
		IndexView idx = moduleIndexes.get(moduleName);
		return idx != null ? idx : mergedIndex;
	}

	/** All module names. */
	public Set<String> modules() {
		return moduleIndexes.keySet();
	}

	/**
	 * All indexed type names, cached from the class-to-module map built during
	 * index loading. Used for cross-module {@code @ConditionalOnBean} visibility
	 * without re-iterating {@code IndexView.getKnownClasses()}.
	 */
	public Set<String> allTypeNames() {
		return allTypeNames;
	}

	/**
	 * Returns the module name a class belongs to, or {@code null} if the class is
	 * not indexed.
	 */
	public String moduleOf(String className) {
		return classToModule.get(className);
	}

	/**
	 * Returns the class names that belong to the given module (and optionally its
	 * transitive dependencies).
	 */
	public Set<String> classesInModule(String moduleName, Set<String> dependencyModules) {
		Set<String> result = new HashSet<>();
		for (var entry : classToModule.entrySet()) {
			if (entry.getValue().equals(moduleName) || dependencyModules.contains(entry.getValue())) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	/**
	 * Returns the union of class names across several modules. Used by the test
	 * framework when a {@code @SummerTest} narrows its universe to an explicit
	 * module set (plus its own module) — the optional narrowing on top of the
	 * Quarkus-wide default.
	 *
	 * @param moduleNames
	 *            modules whose classes should be in scope
	 * @return every class belonging to any of the named modules
	 */
	public Set<String> classesInModules(Set<String> moduleNames) {
		Set<String> result = new HashSet<>();
		for (var entry : classToModule.entrySet()) {
			if (moduleNames.contains(entry.getValue())) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	/**
	 * Creates a scope that includes all classes from the given module and its
	 * transitive dependencies.
	 */
	public Scope toScope(String moduleName, Set<String> dependencyModules) {
		Set<String> included = classesInModule(moduleName, dependencyModules);
		return name -> included.contains(name);
	}

	/**
	 * The Quarkus-aligned wide default: every production class across all modules.
	 * Test classes are excluded by construction — {@link JandexIndexLoader#buildModuleIndex()}
	 * keeps {@code jandex-test.idx} out of the bean universe, so this scope can
	 * never pull in unrelated test fixtures (the sad-path trap).
	 *
	 * @return a scope covering all production beans
	 */
	public Scope productionScope() {
		return name -> classToModule.containsKey(name);
	}
}
