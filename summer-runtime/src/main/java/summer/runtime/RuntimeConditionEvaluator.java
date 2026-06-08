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
 * Phase 2: @Replaces — single pass, mutate registry.
 * </p>
 * <p>
 * Phase 3: @ConditionalOnBean — linear pass in topological order.
 * </p>
 */
final class RuntimeConditionEvaluator {

	private RuntimeConditionEvaluator() {
	}

	static void evaluate(Set<Object> nodes) {
		// Phase 1: Build dependency graph + topological sort
		List<Object> topoOrder = buildTopologicalOrder(nodes);

		// Phase 2: @Replaces — single pass, mutate registry
		resolveReplaces(nodes);

		// Phase 3: @ConditionalOnBean — linear pass in topological order
		resolveConditionalOnBean(nodes, topoOrder);
	}

	// ── Phase 1: Dependency Graph + Topological Sort ──────────────────

	/**
	 * Builds a dependency graph from @ConditionalOnBean annotations and returns
	 * nodes in topological order. Nodes with @ConditionalOnBean come after their
	 * dependencies.
	 */
	private static List<Object> buildTopologicalOrder(Set<Object> nodes) {
		// Build adjacency: node → set of nodes it depends on
		Map<Object, Set<Object>> deps = new HashMap<>();
		for (Object node : nodes) {
			Class<?> requiredType = getRequiredType(node);
			if (requiredType == null)
				continue;

			Set<Object> matches = new HashSet<>();
			for (Object other : nodes) {
				if (requiredType.isAssignableFrom(getProvidedType(other))) {
					matches.add(other);
				}
			}
			if (!matches.isEmpty()) {
				deps.put(node, matches);
			}
		}

		// Topological sort (DFS post-order)
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
		} else if (node instanceof Method method && method.isAnnotationPresent(Bean.class)) {
			ConditionalOnBean cond = method.getAnnotation(ConditionalOnBean.class);
			return cond != null ? cond.value() : null;
		}
		return null;
	}

	// ── Phase 2: @Replaces ────────────────────────────────────────────

	private static void resolveReplaces(Set<Object> nodes) {
		Set<Object> replaced = new HashSet<>();

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
			replaced.add(target);
			// Also remove @Bean methods declared on the replaced class
			for (Object n : nodes) {
				if (n instanceof Method m && m.getDeclaringClass() == targetType) {
					replaced.add(m);
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
			replaced.add(target);
		}

		nodes.removeAll(replaced);
	}

	// ── Phase 3: @ConditionalOnBean ───────────────────────────────────

	/**
	 * Evaluates @ConditionalOnBean in topological order. Single pass — no loops, no
	 * BFS. Dependencies are guaranteed to be evaluated before dependents.
	 */
	private static void resolveConditionalOnBean(Set<Object> nodes, List<Object> topoOrder) {
		for (Object node : topoOrder) {
			if (!nodes.contains(node))
				continue;

			Class<?> requiredType = getRequiredType(node);
			if (requiredType == null)
				continue;

			boolean satisfied = nodes.stream().anyMatch(n -> requiredType.isAssignableFrom(getProvidedType(n)));

			if (!satisfied) {
				nodes.remove(node);
				// If it's a @Configuration class, also remove its @Bean methods
				if (node instanceof Class<?> clazz) {
					nodes.removeIf(n -> n instanceof Method m && m.getDeclaringClass() == clazz);
				}
			}
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
			if (node instanceof Method m && m != replacement && m.getReturnType() == returnType) {
				return node;
			}
		}
		return null;
	}

	private static Class<?> getProvidedType(Object node) {
		if (node instanceof Class<?> c)
			return c;
		if (node instanceof Method m)
			return m.getReturnType();
		return null;
	}
}
