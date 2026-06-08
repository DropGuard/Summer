package summer.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.Component;
import summer.core.ErrorCode;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.BeanCreationException;

/**
 * Discovers Summer components using Jandex indexes.
 *
 * <p>
 * Phase 1 (Discovery) responsibilities:
 * </p>
 * <ul>
 * <li>Delegates index loading to {@link JandexIndexLoader}</li>
 * <li>Resolves meta-annotation chains (e.g., @Configuration → @Component)</li>
 * <li>Discovers @Component-annotated classes</li>
 * <li>Discovers @ConfigurationProperties records</li>
 * <li>Supports manual component registration</li>
 * </ul>
 */
public class ComponentScanner {

	private static final Logger log = LoggerFactory.getLogger(ComponentScanner.class);

	private static final DotName COMPONENT = DotName.createSimple(Component.class);
	private static final DotName CONFIG_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);

	private final Set<Class<?>> componentClasses = new HashSet<>();
	private IndexView lastIndex;

	/**
	 * Discovers @Component-annotated classes (including meta-annotations
	 * like @Configuration, @RestController). Framework beans come from pre-built
	 * Jandex indexes; user beans are discovered by indexing the given packages.
	 */
	public void scan(String... packageNames) {
		IndexView index = JandexIndexLoader.buildIndex(packageNames);
		this.lastIndex = index;
		registerDiscoveredBeans(index);
	}

	/**
	 * Discovers {@code @ConfigurationProperties}-annotated records from the Jandex
	 * index built during {@link #scan(String...)}.
	 */
	public List<Class<?>> discoverConfigurationProperties() {
		if (lastIndex == null) {
			return List.of();
		}
		List<Class<?>> result = new ArrayList<>();
		for (AnnotationInstance ann : lastIndex.getAnnotations(CONFIG_PROPERTIES)) {
			ClassInfo ci = ann.target().asClass();
			if (ci.isInterface() || ci.isAbstract())
				continue;
			try {
				result.add(Class.forName(ci.name().toString()));
			} catch (ClassNotFoundException e) {
				log.debug("[Summer] Could not load @ConfigurationProperties class: {}", ci.name());
			}
		}
		return result;
	}

	/**
	 * Queries the merged index for classes annotated with @Component (directly or
	 * via recursive meta-annotation), then loads and registers them.
	 */
	private void registerDiscoveredBeans(IndexView index) {
		for (ClassInfo classInfo : index.getKnownClasses()) {
			if (classInfo.isInterface() || classInfo.isAbstract())
				continue;

			String className = classInfo.name().toString();
			if (className.contains(".config.generated.") || className.contains("$Generated")) {
				continue;
			}

			if (isBeanClass(classInfo, index)) {
				try {
					componentClasses.add(Class.forName(className));
				} catch (ClassNotFoundException e) {
					log.debug("[Summer] Could not load indexed class: {}", classInfo.name());
				}
			}
		}
		log.debug("[Summer] Registered {} component classes", componentClasses.size());
	}

	/**
	 * Checks if a class is a bean: annotated with @Component (directly or via
	 * recursive meta-annotation chain).
	 */
	private boolean isBeanClass(ClassInfo classInfo, IndexView index) {
		return hasComponentMetaAnnotation(classInfo, index, new HashSet<>());
	}

	private boolean hasComponentMetaAnnotation(ClassInfo classInfo, IndexView index, Set<DotName> visited) {
		if (classInfo == null)
			return false;
		DotName name = classInfo.name();
		if (!visited.add(name))
			return false;

		if (classInfo.hasAnnotation(COMPONENT))
			return true;

		for (AnnotationInstance ann : classInfo.declaredAnnotations()) {
			if (hasComponentMetaAnnotation(index.getClassByName(ann.name()), index, visited)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds a component class directly.
	 */
	public void registerComponent(Class<?> clazz) {
		if (!isComponent(clazz)) {
			throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
					"Class " + clazz.getName() + " is not annotated with @Component");
		}
		componentClasses.add(clazz);
	}

	/**
	 * Checks if a class is a component, including meta-annotated components.
	 */
	private boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class))
			return true;
		for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class))
				return true;
		}
		return false;
	}

	/**
	 * Gets all registered component classes.
	 */
	public Set<Class<?>> getComponentClasses() {
		return componentClasses;
	}
}
