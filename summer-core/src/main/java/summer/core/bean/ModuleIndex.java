package summer.core.bean;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.IndexView;

/**
 * Associates classes with their originating module.
 *
 * <p>
 * Built by {@code JandexIndexLoader} from each {@code META-INF/jandex.idx}
 * location. Enables module-scoped bean discovery — a test can request "only
 * beans from summer-twitter and its dependencies" rather than the full merged
 * index.
 * </p>
 */
public final class ModuleIndex {

	private final IndexView index;
	private final Map<String, String> classToModule;
	private final Set<String> allTypeNames;

	public ModuleIndex(IndexView index, Map<String, String> classToModule) {
		this.index = index;
		this.classToModule = classToModule;
		this.allTypeNames = classToModule.keySet();
	}

	/** The merged index (all classes from all modules). */
	public IndexView index() {
		return index;
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
	 * Returns the set of known module names.
	 */
	public Set<String> modules() {
		return new HashSet<>(classToModule.values());
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
	 * Creates a scope that includes all classes from the given module and its
	 * transitive dependencies.
	 */
	public Scope toScope(String moduleName, Set<String> dependencyModules) {
		Set<String> included = classesInModule(moduleName, dependencyModules);
		return name -> included.contains(name);
	}
}
