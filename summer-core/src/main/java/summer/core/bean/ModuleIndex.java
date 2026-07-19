package summer.core.bean;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.IndexView;

/**
 * Associates classes with their originating module and retains each module's
 * own {@link IndexView} separately — the index is <b>not</b> collapsed into a
 * single merged view. Bean discovery reads the indexes directly, and the set of
 * "known classes" depends entirely on which index was loaded:
 * {@link JandexIndexLoader#applicationIndex()} knows only production classes,
 * while {@link JandexIndexLoader#testIndex()} also knows test-class beans.
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
}
