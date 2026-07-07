package summer.runtime;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.Component;
import summer.core.ErrorCode;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.BeanCreationException;

/**
 * Runtime component scanner. Discovers annotated classes from the Jandex index
 * and resolves transitive dependency closures for local (test) expansion.
 *
 * <p>
 * This class is stateless and thread-safe.
 * </p>
 */
public final class RuntimeComponentScanner {

	private static final Logger log = LoggerFactory.getLogger(RuntimeComponentScanner.class);

	private static final DotName COMPONENT = DotName.createSimple(Component.class);
	private static final DotName REPLACES = DotName.createSimple(Replaces.class);
	private static final DotName CONFIGURATION = DotName.createSimple(Configuration.class);
	private static final DotName BEAN = DotName.createSimple(Bean.class);
	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);
	private static final DotName BEANCONTAINER = DotName.createSimple("summer.core.BeanContainer");

	private RuntimeComponentScanner() {
	}

	/**
	 * Scans the Jandex index for classes annotated with {@code @Component} (or a
	 * meta-annotation) and {@code @ConfigurationProperties}.
	 *
	 * @param index
	 *            the Jandex index to scan
	 * @return mutable set of discovered component classes
	 */
	public static Set<Class<?>> discoverComponents(IndexView index) {
		Set<Class<?>> componentClasses = new LinkedHashSet<>();
		for (ClassInfo classInfo : index.getKnownClasses()) {
			if (classInfo.isInterface() || classInfo.isAbstract()) {
				continue;
			}
			String className = classInfo.name().toString();
			if (className.contains(".config.generated.") || className.contains("$Generated")) {
				continue;
			}
			if (hasMetaComponentAnnotation(classInfo, index, new HashSet<>())) {
				try {
					Class<?> clazz = Class.forName(className);
					componentClasses.add(clazz);
				} catch (ClassNotFoundException e) {
					log.debug("[Summer] Could not load indexed class: {}", classInfo.name());
				}
			}
		}
		log.debug("[Summer] Discovered {} component classes", componentClasses.size());
		return componentClasses;
	}

	/**
	 * Validates that an extra component class is eligible for registration.
	 *
	 * @param clazz
	 *            the class to validate
	 * @throws BeanCreationException
	 *             if the class is invalid
	 */
	private static void validateExtraComponent(Class<?> clazz) {
		if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
			if (isComponent(clazz)) {
				throw new BeanCreationException(
						"@Component cannot be placed on an interface or abstract class: " + clazz.getName()
								+ ". Annotate the concrete implementation instead.");
			}
			return;
		}
		if (!isComponent(clazz) && !clazz.isAnnotationPresent(ConfigurationProperties.class)) {
			throw new BeanCreationException(
					"Class " + clazz.getName() + " is not annotated with @Component or @ConfigurationProperties");
		}
	}

	/**
	 * Computes the transitive dependency closure starting from the given seed
	 * classes. Uses the Jandex index exclusively — no reflection on constructor or
	 * method parameters.
	 *
	 * @param seeds
	 *            initial set of classes
	 * @param index
	 *            Jandex index for dependency resolution
	 * @return mutable set containing seeds + all transitive dependencies
	 */
	public static Set<Class<?>> transitiveExpand(Set<Class<?>> seeds, IndexView index) {
		// Validate all seeds first
		for (Class<?> clazz : seeds) {
			validateExtraComponent(clazz);
		}

		Set<DotName> visited = new LinkedHashSet<>();
		Deque<DotName> queue = new ArrayDeque<>();

		// Seed the BFS
		for (Class<?> seed : seeds) {
			DotName dn = DotName.createSimple(seed.getName());
			if (visited.add(dn)) {
				queue.addLast(dn);
			}
		}

		while (!queue.isEmpty()) {
			DotName currentName = queue.pollFirst();
			ClassInfo current = index.getClassByName(currentName);
			if (current == null) {
				continue;
			}

			// @Replaces target: the replaced class must be in the closure
			// so that SharedConditionEvaluator can find and redirect it.
			AnnotationInstance replacesAnn = current.annotation(REPLACES);
			if (replacesAnn != null) {
				DotName targetName = replacesAnn.value().asClass().name();
				if (visited.add(targetName)) {
					queue.addLast(targetName);
				}
				for (DotName impl : findImplementations(targetName, index)) {
					if (visited.add(impl)) {
						queue.addLast(impl);
					}
				}
			}

			// @ConfigurationProperties have no constructor dependencies
			if (current.hasAnnotation(CONFIGURATION_PROPERTIES)) {
				continue;
			}

			// @Configuration: pull in @Bean method parameter dependencies.
			// Return types are NOT added — they are factory products handled
			// by BeanInstantiator via producerParamTypes.
			if (current.hasAnnotation(CONFIGURATION)) {
				for (MethodInfo method : current.methods()) {
					if (!method.hasAnnotation(BEAN)) {
						continue;
					}
					for (Type paramType : method.parameterTypes()) {
						DotName paramDn = paramType.name();
						if (paramDn.equals(BEANCONTAINER)) {
							continue;
						}
						for (DotName impl : findImplementations(paramDn, index)) {
							if (visited.add(impl)) {
								queue.addLast(impl);
							}
						}
					}
					// Also check method-level @Replaces
					AnnotationInstance beanReplaces = method.annotation(REPLACES);
					if (beanReplaces != null) {
						DotName beanTargetDn = beanReplaces.value().asClass().name();
						if (visited.add(beanTargetDn)) {
							queue.addLast(beanTargetDn);
						}
						for (DotName impl : findImplementations(beanTargetDn, index)) {
							if (visited.add(impl)) {
								queue.addLast(impl);
							}
						}
					}
				}
			}

			// Examine constructor parameters for dependencies
			if (current.constructors().size() != 1) {
				continue; // Let validateConstructor handle this
			}
			for (Type paramType : current.constructors().get(0).parameterTypes()) {
				DotName paramDn = paramType.name();
				if (paramDn.equals(BEANCONTAINER)) {
					continue;
				}
				for (DotName impl : findImplementations(paramDn, index)) {
					if (visited.add(impl)) {
						queue.addLast(impl);
					}
				}
			}
		}

		// Convert DotNames to Class<?>
		Set<Class<?>> result = new LinkedHashSet<>();
		for (DotName dn : visited) {
			try {
				result.add(Class.forName(dn.toString()));
			} catch (ClassNotFoundException e) {
				log.debug("[Summer] Could not load indexed class: {}", dn);
			}
		}

		log.debug("[Summer] Transitive expansion: {} seeds -> {} closure beans", seeds.size(), result.size());
		return result;
	}

	/**
	 * Finds concrete implementations of a dependency type from the Jandex index. If
	 * the type is already a concrete class, returns it directly. If it's an
	 * interface or abstract class, returns all known implementors that are
	 * annotated with {@code @Component} (or a meta-annotation).
	 *
	 * @param type
	 *            the dependency type to resolve
	 * @param index
	 *            Jandex index for implementation lookup
	 * @return list of DotNames of concrete implementations
	 */
	private static List<DotName> findImplementations(DotName type, IndexView index) {
		ClassInfo ci = index.getClassByName(type);
		if (ci != null && !ci.isInterface() && (ci.flags() & Modifier.ABSTRACT) == 0) {
			// Concrete class — if it is a @Component or @ConfigurationProperties,
			// use directly. Otherwise, find the @Configuration that produces it
			// via @Bean.
			if (hasMetaComponentAnnotation(ci, index, new HashSet<>())
					|| ci.hasAnnotation(CONFIGURATION_PROPERTIES)) {
				return List.of(type);
			}
			return findBeanProducers(type, index);
		}

		List<DotName> result = new ArrayList<>();
		for (ClassInfo impl : index.getKnownDirectImplementations(type)) {
			if (impl.isInterface() || (impl.flags() & Modifier.ABSTRACT) != 0) {
				continue;
			}
			if (hasMetaComponentAnnotation(impl, index, new HashSet<>())) {
				result.add(impl.name());
			}
		}
		// If no direct @Component implementations found, look for @Bean producers
		if (result.isEmpty()) {
			result.addAll(findBeanProducers(type, index));
		}
		return result;
	}

	/**
	 * Finds {@code @Configuration} classes that have a {@code @Bean} method
	 * returning the given type.
	 */
	private static List<DotName> findBeanProducers(DotName targetName, IndexView index) {
		List<DotName> result = new ArrayList<>();
		for (AnnotationInstance beanAnn : index.getAnnotations(BEAN)) {
			if (beanAnn.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
				continue;
			}
			MethodInfo method = beanAnn.target().asMethod();
			if (method.returnType().name().equals(targetName)) {
				ClassInfo configClass = method.declaringClass();
				if (hasMetaComponentAnnotation(configClass, index, new HashSet<>())) {
					result.add(configClass.name());
				}
			}
		}
		return result;
	}

	/**
	 * Checks whether a class has {@code @Component} or a meta-annotation that is
	 * itself annotated with {@code @Component}.
	 *
	 * @param clazz
	 *            the class to check
	 * @return true if the class is a component
	 */
	private static boolean isComponent(Class<?> clazz) {
		if (clazz.isAnnotationPresent(Component.class)) {
			return true;
		}
		for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
			if (ann.annotationType().isAnnotationPresent(Component.class)) {
				return true;
			}
		}
		return false;
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
