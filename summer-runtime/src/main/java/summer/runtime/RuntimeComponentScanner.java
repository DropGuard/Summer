package summer.runtime;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.Component;
import summer.core.bean.ModuleIndex;
import summer.core.config.ConfigurationProperties;

/**
 * Runtime component scanner. Discovers annotated classes from the Jandex index.
 *
 * <p>
 * Discovery honours module boundaries natively: when given a
 * {@link ModuleIndex} it iterates only that index's per-module views (and only
 * loads classes from modules present in it), so a test universe built from
 * {@code
 * JandexIndexLoader.testIndex()} sees exactly the production-plus-test beans on
 * its classpath — no post-hoc narrowing switch. This class is stateless and
 * thread-safe.
 * </p>
 */
public final class RuntimeComponentScanner {

	private static final Logger log = LoggerFactory.getLogger(RuntimeComponentScanner.class);

	private static final DotName COMPONENT = DotName.createSimple(Component.class);
	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);

	private RuntimeComponentScanner() {
	}

	/**
	 * Scans a {@link ModuleIndex} for component classes, iterating only the indexes
	 * of modules it retains. The full merged index is still used for annotation
	 * resolution ({@link #hasMetaComponentAnnotation} calls {@code
	 * index.getClassByName()}), but class loading ({@code Class.forName}) only
	 * happens for classes the index actually knows — unrelated modules' sad-path
	 * constructors never fire.
	 *
	 * @param moduleIndex
	 *            the module-index with per-module index retention
	 * @param mergedIndex
	 *            the merged CompositeIndex for annotation lookups
	 * @return mutable set of discovered component classes
	 */
	public static Set<Class<?>> discoverComponents(ModuleIndex moduleIndex, IndexView mergedIndex) {
		Set<Class<?>> componentClasses = new LinkedHashSet<>();
		for (String mod : moduleIndex.modules()) {
			IndexView modIdx = moduleIndex.moduleIndex(mod);
			for (ClassInfo classInfo : modIdx.getKnownClasses()) {
				if (classInfo.isInterface() || classInfo.isAbstract()) {
					continue;
				}
				String className = classInfo.name().toString();
				if (className.contains(".config.generated.") || className.contains("$Generated")) {
					continue;
				}
				boolean isComponent = hasMetaComponentAnnotation(classInfo, mergedIndex, new HashSet<>());
				boolean isConfigProps = classInfo.hasAnnotation(CONFIGURATION_PROPERTIES);
				if (isComponent || isConfigProps) {
					try {
						Class<?> clazz = Class.forName(className);
						componentClasses.add(clazz);
					} catch (ClassNotFoundException e) {
						log.debug("[Summer] Could not load indexed class: {}", classInfo.name());
					}
				}
			}
		}
		log.debug("[Summer] Discovered {} component classes from ModuleIndex", componentClasses.size());
		return componentClasses;
	}

	/**
	 * Recursively checks whether a Jandex {@link ClassInfo} has {@code @Component}
	 * on itself or on any of its annotation types (meta-annotation detection).
	 *
	 * @param classInfo
	 *            the class info to check
	 * @param index
	 *            Jandex index for annotation resolution
	 * @param visited
	 *            cycle guard
	 * @return true if the class has a component meta-annotation
	 */
	private static boolean hasMetaComponentAnnotation(ClassInfo classInfo, IndexView index, Set<DotName> visited) {
		if (classInfo == null) {
			return false;
		}
		DotName name = classInfo.name();
		if (!visited.add(name)) {
			return false;
		}
		if (classInfo.hasAnnotation(COMPONENT)) {
			return true;
		}
		for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
			if (hasMetaComponentAnnotation(index.getClassByName(ann.name()), index, visited)) {
				return true;
			}
		}
		return false;
	}
}
