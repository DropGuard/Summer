package summer.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

final class RuntimeConditionEvaluator {

	private RuntimeConditionEvaluator() {
	}

	static void evaluate(Set<Object> nodes) {
		// 1. Evaluate @ConditionalOnBean first
		resolveConditionalOnBean(nodes);

		// 2. Then evaluate @Replaces
		resolveReplaces(nodes);
	}

	private static void resolveConditionalOnBean(Set<Object> nodes) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Object node : new ArrayList<>(nodes)) {
				if (node instanceof Class<?> clazz) {
					// Check class-level @ConditionalOnBean
					ConditionalOnBean cond = clazz.getAnnotation(ConditionalOnBean.class);
					if (cond == null)
						continue;

					Class<?> requiredType = cond.value();
					boolean satisfied = nodes.stream()
							.anyMatch(n -> requiredType.isAssignableFrom(getProvidedType(n)) && !isInterfaceNode(n));

					if (!satisfied) {
						nodes.remove(node);
						// Also remove all @Bean methods declared in this class
						nodes.removeIf(n -> n instanceof Method m && m.getDeclaringClass() == clazz);
						changed = true;
					}
				} else if (node instanceof Method method) {
					// Check method-level @ConditionalOnBean on @Bean methods
					if (!method.isAnnotationPresent(Bean.class))
						continue;
					ConditionalOnBean cond = method.getAnnotation(ConditionalOnBean.class);
					if (cond == null)
						continue;

					Class<?> requiredType = cond.value();
					boolean satisfied = nodes.stream()
							.anyMatch(n -> requiredType.isAssignableFrom(getProvidedType(n)) && !isInterfaceNode(n));

					if (!satisfied) {
						nodes.remove(node);
						changed = true;
					}
				}
			}
		}
	}

	private static void resolveReplaces(Set<Object> nodes) {
		Set<Object> replaced = new HashSet<>();

		// 1. Class-level @Replaces
		for (Object node : new ArrayList<>(nodes)) {
			if (!(node instanceof Class<?> clazz))
				continue;

			Replaces replaces = clazz.getAnnotation(Replaces.class);
			if (replaces == null)
				continue;

			Class<?> targetType = replaces.value();
			Object target = findNodeByType(nodes, targetType);
			if (target == null) {
				throw new NoSuchBeanException("@Replaces target not found: " + targetType.getName());
			}
			replaced.add(target);
		}

		// 2. Method-level @Replaces (on @Bean methods)
		for (Object node : new ArrayList<>(nodes)) {
			if (!(node instanceof Method method))
				continue;
			if (!method.isAnnotationPresent(Bean.class))
				continue;

			Replaces replaces = method.getAnnotation(Replaces.class);
			if (replaces == null)
				continue;

			Class<?> targetType = replaces.value();
			Object target = findNodeByReturnType(nodes, targetType, method);
			if (target == null) {
				throw new NoSuchBeanException("@Replaces target not found: " + targetType.getName());
			}
			replaced.add(target);
		}

		nodes.removeAll(replaced);
	}

	private static Object findNodeByType(Set<Object> nodes, Class<?> type) {
		for (Object node : nodes) {
			if (node instanceof Class<?> clazz && clazz == type) {
				return node;
			}
		}
		return null;
	}

	private static Object findNodeByReturnType(Set<Object> nodes, Class<?> returnType, Method replacement) {
		Object found = null;
		for (Object node : nodes) {
			if (node == replacement)
				continue;
			if (node instanceof Method method && method.isAnnotationPresent(Bean.class)) {
				if (method.getReturnType() == returnType) {
					if (found != null) {
						throw new AmbiguousBeanException("Ambiguous @Replaces: multiple @Bean methods return "
								+ returnType.getName() + ": " + ((Method) found).getDeclaringClass().getName() + "."
								+ ((Method) found).getName() + " and " + method.getDeclaringClass().getName() + "."
								+ method.getName());
					}
					found = node;
				}
			}
		}
		return found;
	}

	private static Class<?> getProvidedType(Object node) {
		if (node instanceof Class<?> clazz)
			return clazz;
		if (node instanceof Method method)
			return method.getReturnType();
		return Object.class;
	}

	private static boolean isInterfaceNode(Object node) {
		if (node instanceof Class<?> clazz)
			return clazz.isInterface();
		return false;
	}
}
