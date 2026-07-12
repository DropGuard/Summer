package summer.core.bean;

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
import summer.core.exception.BeanCreationException;

/**
 * Computes the transitive dependency closure from a set of seed classes using
 * the Jandex index exclusively — no reflection on constructor or method
 * parameters.
 *
 * <p>
 * This is the single, shared BFS implementation used by both the Runtime and
 * AOT engines. It replaces the former
 * {@code RuntimeComponentScanner.transitiveExpand} and
 * {@code LocalContextGenerator.transitiveClosure}.
 * </p>
 *
 * <p>
 * The BFS walks:
 * </p>
 * <ul>
 * <li>Constructor parameters</li>
 * <li>{@code @Bean} method parameter dependencies (not return types — those
 * are factory products discovered from the {@code @Configuration} class)</li>
 * <li>{@code @Replaces} targets (class-level and method-level)</li>
 * <li>{@code List<T>} element types</li>
 * </ul>
 */
public final class BeanClosure {

	private static final DotName COMPONENT = DotName.createSimple("summer.core.Component");
	private static final DotName REPLACES = DotName.createSimple("summer.core.annotation.Replaces");
	private static final DotName CONFIGURATION = DotName.createSimple("summer.core.annotation.Configuration");
	private static final DotName BEAN = DotName.createSimple("summer.core.annotation.Bean");
	private static final DotName CONFIGURATION_PROPERTIES = DotName.createSimple("summer.core.config.ConfigurationProperties");
	private static final DotName BEAN_CONTAINER = DotName.createSimple("summer.core.BeanContainer");

	private BeanClosure() {
	}

	/**
	 * Computes the transitive dependency closure starting from the given seed
	 * class names.
	 *
	 * @param seeds
	 *            fully-qualified class names of the entry beans
	 * @param index
	 *            Jandex index for dependency resolution
	 * @return mutable set containing seeds + all transitive dependencies
	 */
	public static Set<String> compute(Set<String> seeds, IndexView index) {
		Set<String> closure = new LinkedHashSet<>(seeds);
		Deque<String> queue = new ArrayDeque<>(seeds);

		while (!queue.isEmpty()) {
			String currentName = queue.pollFirst();
			ClassInfo current = index.getClassByName(DotName.createSimple(currentName));
			if (current == null) {
				continue;
			}

			// Interfaces / abstract classes: expand known implementations
			if (current.isInterface() || current.isAbstract()) {
				for (DotName impl : findImplementations(current.name(), index)) {
					if (closure.add(impl.toString())) {
						queue.addLast(impl.toString());
					}
				}
				continue;
			}

			// @Replaces target: the replaced class must be in the closure
			// so that SharedConditionEvaluator can find and redirect it.
			AnnotationInstance replacesAnn = current.annotation(REPLACES);
			if (replacesAnn != null) {
				String targetName = replacesAnn.value().asClass().name().toString();
				if (closure.add(targetName)) {
					queue.addLast(targetName);
				}
				for (DotName impl : findImplementations(replacesAnn.value().asClass().name(), index)) {
					if (closure.add(impl.toString())) {
						queue.addLast(impl.toString());
					}
				}
			}

			// @ConfigurationProperties have no constructor dependencies
			if (current.hasAnnotation(CONFIGURATION_PROPERTIES)) {
				continue;
			}

			// @Configuration: pull in @Bean method parameter dependencies.
			// Return types are NOT added — they are factory products handled
			// by the discovery phase via the @Configuration class.
			if (current.hasAnnotation(CONFIGURATION)) {
				for (MethodInfo method : current.methods()) {
					if (!method.hasAnnotation(BEAN)) {
						continue;
					}
					// Walk @Bean method parameters
					for (Type paramType : method.parameterTypes()) {
						DotName paramDn = unwrapListElement(paramType);
						if (paramDn.equals(BEAN_CONTAINER)) {
							continue;
						}
						for (DotName impl : findImplementations(paramDn, index)) {
							if (closure.add(impl.toString())) {
								queue.addLast(impl.toString());
							}
						}
					}
					// Method-level @Replaces
					AnnotationInstance beanReplaces = method.annotation(REPLACES);
					if (beanReplaces != null) {
						String beanTargetName = beanReplaces.value().asClass().name().toString();
						if (closure.add(beanTargetName)) {
							queue.addLast(beanTargetName);
						}
						for (DotName impl : findImplementations(beanReplaces.value().asClass().name(), index)) {
							if (closure.add(impl.toString())) {
								queue.addLast(impl.toString());
							}
						}
					}
				}
			}

			// Examine constructor parameters for dependencies
			MethodInfo ctor = findPublicConstructor(current);
			if (ctor == null) {
				continue;
			}
			for (Type paramType : ctor.parameterTypes()) {
				DotName paramDn = unwrapListElement(paramType);
				if (paramDn.equals(BEAN_CONTAINER)) {
					continue;
				}
				for (DotName impl : findImplementations(paramDn, index)) {
					if (closure.add(impl.toString())) {
						queue.addLast(impl.toString());
					}
				}
			}
		}

		return closure;
	}

	/**
	 * Validates that all seed classes are eligible for registration: not
	 * interfaces/abstract with {@code @Component}, and annotated with
	 * {@code @Component} or {@code @ConfigurationProperties}.
	 *
	 * @param seeds
	 *            seed class names
	 * @param index
	 *            Jandex index
	 * @throws BeanCreationException
	 *             if any seed is invalid
	 */
	public static void validateSeeds(Set<String> seeds, IndexView index) {
		for (String seedName : seeds) {
			ClassInfo ci = index.getClassByName(DotName.createSimple(seedName));
			if (ci == null) {
				// Not in index — skip validation (will be caught later)
				continue;
			}
			if (ci.isInterface() || ci.isAbstract()) {
				if (hasMetaComponentAnnotation(ci, index, new HashSet<>())) {
					throw new BeanCreationException(
							"@Component cannot be placed on an interface or abstract class: " + seedName
									+ ". Annotate the concrete implementation instead.");
				}
				continue;
			}
			if (!hasMetaComponentAnnotation(ci, index, new HashSet<>())
					&& !ci.hasAnnotation(CONFIGURATION_PROPERTIES)) {
				throw new BeanCreationException(
						"Class " + seedName + " is not annotated with @Component or @ConfigurationProperties");
			}
		}
	}

	// ── Implementation resolution ────────────────────────────────────

	/**
	 * Finds concrete implementations of a dependency type from the Jandex index.
	 * If the type is already a concrete class, returns it directly. If it's an
	 * interface or abstract class, returns all known concrete implementors that
	 * are annotated with {@code @Component} (or a meta-annotation). If no
	 * component implementations are found, falls back to {@code @Bean} producers.
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
	private static List<DotName> findBeanProducers(DotName targetType, IndexView index) {
		List<DotName> result = new ArrayList<>();
		for (AnnotationInstance beanAnn : index.getAnnotations(BEAN)) {
			if (beanAnn.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
				continue;
			}
			MethodInfo method = beanAnn.target().asMethod();
			if (method.returnType().name().equals(targetType)) {
				ClassInfo configClass = method.declaringClass();
				if (hasMetaComponentAnnotation(configClass, index, new HashSet<>())) {
					result.add(configClass.name());
				}
			}
		}
		return result;
	}

	// ── Annotation helpers ───────────────────────────────────────────

	/**
	 * Recursively checks whether a Jandex {@link ClassInfo} has
	 * {@code @Component} on itself or on any of its annotation types
	 * (meta-annotation detection).
	 */
	static boolean hasMetaComponentAnnotation(ClassInfo classInfo, IndexView index, Set<DotName> visited) {
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

	// ── Utility ──────────────────────────────────────────────────────

	/**
	 * If the type is {@code List<T>}, returns the DotName of {@code T}. Otherwise
	 * returns the type's own DotName.
	 */
	private static DotName unwrapListElement(Type paramType) {
		DotName dn = paramType.name();
		if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE && dn.toString().equals("java.util.List")) {
			return paramType.asParameterizedType().arguments().get(0).name();
		}
		return dn;
	}

	/**
	 * Finds the single public constructor of a class, or {@code null} if none or
	 * multiple exist.
	 */
	private static MethodInfo findPublicConstructor(ClassInfo ci) {
		MethodInfo found = null;
		for (MethodInfo method : ci.methods()) {
			if (method.name().equals("<init>") && (method.flags() & 0x0001) != 0) {
				if (found != null) {
					return null; // multiple public constructors
				}
				found = method;
			}
		}
		return found;
	}
}
