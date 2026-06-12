package summer.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Replaces;
import summer.core.exception.NoSuchBeanException;

/**
 * Three-phase condition evaluator for the Runtime DI engine.
 *
 * <p>
 * Phase 1: Discovery — build dependency graph from @ConditionalOnBean
 * annotations, topological sort.
 * </p>
 * <p>
 * Phase 2: @Replaces — mark redirects (original → replacement). Original bean
 * stays in registry but points to its replacement.
 * </p>
 * <p>
 * Phase 3: @ConditionalOnBean — evaluate conditions in topological order.
 * Resolve through redirects. If a replacement is removed, restore the original.
 * </p>
 */
final class RuntimeConditionEvaluator {

	private RuntimeConditionEvaluator() {
	}

	static void evaluate(Set<Object> nodes) {
		// Phase 1: Build dependency graph + topological sort
		List<Object> topoOrder = buildTopologicalOrder(nodes);

		// Phase 1.5: Pre-compute @Bean return types for @ConditionalOnBean evaluation
		Set<Class<?>> beanReturnTypes = new HashSet<>();
		for (Object node : nodes) {
			if (node instanceof Class<?> clazz && clazz.isAnnotationPresent(summer.core.annotation.Configuration.class)) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Bean.class)) {
						beanReturnTypes.add(method.getReturnType());
					}
				}
			}
		}

		// Phase 2: @Replaces — mark redirects, don't remove
		Map<Object, Object> redirects = new HashMap<>();
		resolveReplaces(nodes, redirects);

		// Phase 3: @ConditionalOnBean — evaluate conditions, resolve through redirects
		resolveConditionalOnBean(nodes, topoOrder, redirects, beanReturnTypes);
	}

	// ── Phase 1: Dependency Graph + Topological Sort ──────────────────

	/**
	 * Builds a dependency graph from @ConditionalOnBean annotations and returns
	 * nodes in topological order. Nodes with @ConditionalOnBean come after their
	 * dependencies.
	 */
	private static List<Object> buildTopologicalOrder(Set<Object> nodes) {
		Map<Object, Set<Object>> deps = new HashMap<>();
		for (Object node : nodes) {
			Class<?> required = getRequiredType(node);
			if (required != null) {
				deps.computeIfAbsent(node, k -> new HashSet<>()).add(required);
			}
		}

		Set<Object> visited = new HashSet<>();
		Set<Object> inStack = new HashSet<>();
		List<Object> order = new ArrayList<>();

		for (Object node : nodes) {
			dfs(node, deps, visited, inStack, order);
		}

		return order;
	}

	private static void dfs(Object node, Map<Object, Set<Object>> deps, Set<Object> visited, Set<Object> inStack,
			List<Object> order) {
		if (visited.contains(node))
			return;
		visited.add(node);
		inStack.add(node);

		Set<Object> nodeDeps = deps.getOrDefault(node, Set.of());
		for (Object dep : nodeDeps) {
			if (!visited.contains(dep)) {
				dfs(dep, deps, visited, inStack, order);
			}
		}

		inStack.remove(node);
		order.add(node);
	}

	/**
	 * Returns the required type from @ConditionalOnBean, or null if the node has no
	 * condition.
	 */
	private static Class<?> getRequiredType(Object node) {
		if (node instanceof Class<?> clazz) {
			ConditionalOnBean cond = clazz.getAnnotation(ConditionalOnBean.class);
			return cond != null ? cond.value() : null;
		}
		if (node instanceof Method method) {
			ConditionalOnBean cond = method.getAnnotation(ConditionalOnBean.class);
			return cond != null ? cond.value() : null;
		}
		return null;
	}

	// ── Phase 2: @Replaces ────────────────────────────────────────────

	/**
	 * Marks redirects for @Replaces. Original beans stay in the registry but point
	 * to their replacements.
	 */
	private static void resolveReplaces(Set<Object> nodes, Map<Object, Object> redirects) {
		// Class-level @Replaces
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
			redirects.put(target, node);
			// Also redirect @Bean methods declared on the replaced class
			for (Object n : nodes) {
				if (n instanceof Method m && m.getDeclaringClass() == targetType) {
					redirects.put(m, node);
				}
			}
		}

		// Method-level @Replaces (on @Bean methods)
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
			redirects.put(target, node);
		}
	}

	// ── Phase 3: @ConditionalOnBean ───────────────────────────────────

	/**
	 * Evaluates @ConditionalOnBean in topological order. Resolves through
	 * redirects. If a replacement is removed, restores the original.
	 */
	private static void resolveConditionalOnBean(Set<Object> nodes, List<Object> topoOrder,
			Map<Object, Object> redirects, Set<Class<?>> beanReturnTypes) {
		for (Object node : topoOrder) {
			if (!nodes.contains(node))
				continue;

			Class<?> requiredType = getRequiredType(node);
			if (requiredType == null)
				continue;

			// Check if the required type is satisfied, resolving through redirects
			boolean satisfied = false;
			for (Object n : nodes) {
				Class<?> providedType = getProvidedType(n);
				if (providedType == null)
					continue;
				// Direct match
				if (requiredType.isAssignableFrom(providedType)) {
					satisfied = true;
					break;
				}
				// Redirect match: n is redirected, check if redirect target provides the type
				Object redirectTarget = redirects.get(n);
				if (redirectTarget != null) {
					Class<?> redirectType = getProvidedType(redirectTarget);
					if (redirectType != null && requiredType.isAssignableFrom(redirectType)) {
						satisfied = true;
						break;
					}
				}
			}
			// Check @Bean return types (not yet in nodes but declared by surviving configs)
			if (!satisfied) {
				for (Class<?> returnType : beanReturnTypes) {
					if (requiredType.isAssignableFrom(returnType)) {
						satisfied = true;
						break;
					}
				}
			}

			if (!satisfied) {
				nodes.remove(node);
				// If it's a @Configuration class, also remove its @Bean methods
				if (node instanceof Class<?> clazz) {
					nodes.removeIf(n -> n instanceof Method m && m.getDeclaringClass() == clazz);
				}
				// If this node was a replacement, restore the original
				redirects.entrySet().removeIf(entry -> {
					if (entry.getValue() == node) {
						// Original is no longer redirected — it survives
						return true;
					}
					return false;
				});
			} else {
			}
		}
		// Cleanup: remove original beans whose replacements survived
		for (Map.Entry<Object, Object> entry : new ArrayList<>(redirects.entrySet())) {
			Object original = entry.getKey();
			Object replacement = entry.getValue();
			if (nodes.contains(replacement)) {
				// Replacement survived — remove original
				nodes.remove(original);
				if (original instanceof Class<?> clazz) {
					nodes.removeIf(n -> n instanceof Method m && m.getDeclaringClass() == clazz);
				}
			}
			// If replacement was removed, original stays (redirect already cleared above)
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────

	private static Object findNodeByType(Set<Object> nodes, Class<?> type) {
		for (Object node : nodes) {
			if (getProvidedType(node) == type) {
				return node;
			}
		}
		return null;
	}

	private static Object findNodeByReturnType(Set<Object> nodes, Class<?> returnType, Method replacement) {
		for (Object node : nodes) {
			if (node instanceof Method m && m.getReturnType() == returnType && m != replacement) {
				return m;
			}
		}
		return null;
	}

	private static Class<?> getProvidedType(Object node) {
		if (node instanceof Class<?> clazz) {
			return clazz;
		}
		if (node instanceof Method method) {
			return method.getReturnType();
		}
		return null;
	}
}
