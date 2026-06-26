package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple(ConfigurationProperties.class);

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
				throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
						"@Component cannot be placed on an interface or abstract class: " + clazz.getName()
								+ ". Annotate the concrete implementation instead.");
			}
			return;
		}
		if (!isComponent(clazz) && !clazz.isAnnotationPresent(ConfigurationProperties.class)) {
			throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
					"Class " + clazz.getName() + " is not annotated with @Component or @ConfigurationProperties");
		}
	}

	/**
	 * Computes the transitive dependency closure starting from the given seed
	 * classes. Uses the Jandex index to resolve interface/abstract dependencies to
	 * their concrete implementations.
	 *
	 * @param seeds
	 *            initial set of classes
	 * @param index
	 *            Jandex index for implementation discovery
	 * @return mutable set containing seeds + all transitive dependencies
	 */
	public static Set<Class<?>> transitiveExpand(Set<Class<?>> seeds, IndexView index) {
		// Validate all seeds first
		for (Class<?> clazz : seeds) {
			validateExtraComponent(clazz);
		}

		Set<Class<?>> closure = new LinkedHashSet<>(seeds);
		Deque<Class<?>> queue = new ArrayDeque<>(seeds);

		while (!queue.isEmpty()) {
			Class<?> current = queue.pollFirst();

			// @Replaces target: the replaced class must be in the closure
			// so that SharedConditionEvaluator can find and redirect it.
			// If the target is an interface/abstract, also pull in its
			// known implementations so they can be replaced.
			Replaces replaces = current.getAnnotation(Replaces.class);
			if (replaces != null) {
				Class<?> target = replaces.value();
				if (closure.add(target)) {
					queue.addLast(target);
				}
				for (Class<?> impl : findImplementations(target, index)) {
					if (closure.add(impl)) {
						queue.addLast(impl);
					}
				}
			}

			// @ConfigurationProperties have no constructor dependencies
			if (current.isAnnotationPresent(ConfigurationProperties.class)) {
				continue;
			}

			// @Configuration: pull in @Bean method parameter dependencies.
			// Return types are NOT added — they are factory products handled
			// by BeanInstantiator via producerParamTypes.
			if (current.isAnnotationPresent(Configuration.class)) {
				for (Method method : current.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Bean.class)) {
						for (Class<?> paramType : method.getParameterTypes()) {
							if (paramType == summer.core.BeanContainer.class) {
								continue;
							}
							List<Class<?>> paramImpls = findImplementations(paramType, index);
							for (Class<?> impl : paramImpls) {
								if (closure.add(impl)) {
									queue.addLast(impl);
								}
							}
						}
						// Also check method-level @Replaces
						Replaces beanReplaces = method.getAnnotation(Replaces.class);
						if (beanReplaces != null) {
							Class<?> beanTarget = beanReplaces.value();
							if (closure.add(beanTarget)) {
								queue.addLast(beanTarget);
							}
							for (Class<?> impl : findImplementations(beanTarget, index)) {
								if (closure.add(impl)) {
									queue.addLast(impl);
								}
							}
						}
					}
				}
			}

			// Examine constructor parameters for dependencies
			Constructor<?>[] constructors = current.getConstructors();
			if (constructors.length != 1) {
				continue; // Let validateConstructor handle this
			}
			Constructor<?> ctor = constructors[0];
			for (Class<?> paramType : ctor.getParameterTypes()) {
				if (paramType == summer.core.BeanContainer.class) {
					continue;
				}

				// Discover implementations from the Jandex index
				List<Class<?>> impls = findImplementations(paramType, index);
				for (Class<?> impl : impls) {
					if (closure.add(impl)) {
						queue.addLast(impl);
					}
				}
			}
		}

		log.debug("[Summer] Transitive expansion: {} seeds -> {} closure beans", seeds.size(), closure.size());
		return closure;
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
	 * @return list of concrete implementation classes
	 */
	private static List<Class<?>> findImplementations(Class<?> type, IndexView index) {
		if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
			// Concrete class — if it is a @Component or @ConfigurationProperties,
			// use directly. Otherwise, find the @Configuration that produces it
			// via @Bean.
			if (isComponent(type) || type.isAnnotationPresent(ConfigurationProperties.class)) {
				return List.of(type);
			}
			return findBeanProducers(type, index);
		}

		List<Class<?>> result = new ArrayList<>();
		DotName dotName = DotName.createSimple(type.getName());
		for (ClassInfo ci : index.getKnownDirectImplementations(dotName)) {
			if (ci.isInterface() || ci.isAbstract()) {
				continue;
			}
			if (hasMetaComponentAnnotation(ci, index, new HashSet<>())) {
				try {
					result.add(Class.forName(ci.name().toString()));
				} catch (ClassNotFoundException e) {
					log.debug("[Summer] Could not load implementation: {}", ci.name());
				}
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
	private static List<Class<?>> findBeanProducers(Class<?> type, IndexView index) {
		List<Class<?>> result = new ArrayList<>();
		DotName targetName = DotName.createSimple(type.getName());
		for (AnnotationInstance beanAnn : index.getAnnotations(Bean.class)) {
			if (beanAnn.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
				continue;
			}
			org.jboss.jandex.MethodInfo method = beanAnn.target().asMethod();
			if (method.returnType().name().equals(targetName)) {
				ClassInfo configClass = method.declaringClass();
				if (hasMetaComponentAnnotation(configClass, index, new HashSet<>())) {
					try {
						result.add(Class.forName(configClass.name().toString()));
					} catch (ClassNotFoundException e) {
						log.debug("[Summer] Could not load @Configuration class: {}", configClass.name());
					}
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
