package summer.compiler;

import java.util.*;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/**
 * Compile-time dependency resolver that builds a dependency graph from
 * {@link AptBeanDefinition}s and produces a topologically sorted instantiation
 * order.
 *
 * <p>
 * Uses qualified name strings for type matching.
 */
final class DependencyResolver {

	private final Messager messager;

	DependencyResolver(Messager messager) {
		this.messager = messager;
	}

	/**
	 * Resolves all inter-bean dependencies and returns the beans in topological
	 * (dependency-first) order. Reports errors via {@link Messager} if circular
	 * dependencies are detected or a required dependency cannot be found.
	 *
	 * @param beans
	 *            all discovered bean definitions
	 * @return sorted list (leaves first), or empty list on error
	 */
	List<AptBeanDefinition> resolve(List<AptBeanDefinition> beans) {
		// 1. Resolve each bean's dependencies to concrete AptBeanDefinition references
		for (AptBeanDefinition bean : beans) {
			resolveDependencies(bean, beans);
		}

		// 2. Link FACTORY_PRODUCT beans to their @Configuration bean
		for (AptBeanDefinition bean : beans) {
			if (bean.kind == AptBeanDefinition.Kind.FACTORY_PRODUCT) {
				linkConfigBean(bean, beans);
			}
		}

		// 3. Cycle detection
		if (hasCycle(beans)) {
			return Collections.emptyList();
		}

		// 4. Topological sort
		return topologicalSort(beans);
	}

	// -----------------------------------------------------------------------
	// Dependency resolution
	// -----------------------------------------------------------------------

	private void resolveDependencies(AptBeanDefinition bean, List<AptBeanDefinition> allBeans) {
		List<String> paramTypes;
		if (bean.kind == AptBeanDefinition.Kind.FACTORY_PRODUCT) {
			paramTypes = bean.producerParamTypes();
		} else {
			paramTypes = bean.constructorParamTypes();
		}

		bean.resolvedDependencies.clear();
		for (String paramType : paramTypes) {
			if (paramType.equals("summer.core.ApplicationContext")) {
				continue; // Self-injected dependency
			}
			AptBeanDefinition resolved = findBean(paramType, allBeans);
			if (resolved == null) {
				messager.printMessage(Diagnostic.Kind.ERROR,
						"No bean found for dependency type: " + paramType + " required by " + bean.qualifiedName());
			} else {
				bean.resolvedDependencies.add(resolved);
			}
		}
	}

	private void linkConfigBean(AptBeanDefinition factoryProduct, List<AptBeanDefinition> allBeans) {
		String configClassName = factoryProduct.configClassName();
		for (AptBeanDefinition candidate : allBeans) {
			if (candidate.kind == AptBeanDefinition.Kind.CONFIGURATION
					&& candidate.qualifiedName().equals(configClassName)) {
				factoryProduct.configBeanDefinition = candidate;
				return;
			}
		}
		messager.printMessage(Diagnostic.Kind.ERROR,
				"Could not find @Configuration bean for factory product: " + factoryProduct.qualifiedName());
	}

	/**
	 * Finds a bean matching the requested paramType. First tries exact type match,
	 * then matches by interface implementation.
	 */
	AptBeanDefinition findBean(String paramType, List<AptBeanDefinition> allBeans) {
		// Pass 1: exact type match
		for (AptBeanDefinition candidate : allBeans) {
			if (candidate.qualifiedName().equals(paramType)) {
				return candidate;
			}
		}

		// Pass 2: interface → implementation
		List<AptBeanDefinition> matches = new ArrayList<>();
		for (AptBeanDefinition candidate : allBeans) {
			if (candidate.interfaceNames().contains(paramType)) {
				matches.add(candidate);
			}
		}

		if (matches.size() == 1) {
			return matches.getFirst();
		}

		if (matches.size() > 1) {
			throw new summer.core.SummerException(summer.core.ErrorCode.AMBIGUOUS_BEAN,
					"Ambiguous dependency. Multiple beans found for type: " + paramType);
		}

		return null;
	}

	// -----------------------------------------------------------------------
	// Cycle detection (DFS-based
	// -----------------------------------------------------------------------

	private boolean hasCycle(List<AptBeanDefinition> beans) {
		Set<AptBeanDefinition> visited = new HashSet<>();
		Set<AptBeanDefinition> stack = new HashSet<>();

		for (AptBeanDefinition bean : beans) {
			if (hasCycleDfs(bean, visited, stack)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasCycleDfs(AptBeanDefinition bean, Set<AptBeanDefinition> visited, Set<AptBeanDefinition> stack) {
		if (stack.contains(bean)) {
			messager.printMessage(Diagnostic.Kind.ERROR,
					"Circular dependency detected involving bean: " + bean.qualifiedName());
			return true;
		}
		if (visited.contains(bean)) {
			return false;
		}

		visited.add(bean);
		stack.add(bean);

		for (AptBeanDefinition dep : bean.resolvedDependencies) {
			if (dep != null && hasCycleDfs(dep, visited, stack)) {
				return true;
			}
		}
		// Also traverse config bean dependency for FACTORY_PRODUCT
		if (bean.configBeanDefinition != null) {
			if (hasCycleDfs(bean.configBeanDefinition, visited, stack)) {
				return true;
			}
		}
		// Also traverse AOP interceptor dependencies
		if (bean.needsProxy) {
			for (AptBeanDefinition interceptor : bean.interceptors) {
				if (hasCycleDfs(interceptor, visited, stack)) {
					return true;
				}
			}
		}

		stack.remove(bean);
		return false;
	}

	// -----------------------------------------------------------------------
	// Topological sort (Kahn's algorithm
	// -----------------------------------------------------------------------

	private List<AptBeanDefinition> topologicalSort(List<AptBeanDefinition> beans) {
		// Build adjacency: bean → set of beans it depends on
		Map<AptBeanDefinition, Set<AptBeanDefinition>> incoming = new LinkedHashMap<>();
		for (AptBeanDefinition b : beans) {
			incoming.put(b, new LinkedHashSet<>());
		}
		for (AptBeanDefinition b : beans) {
			for (AptBeanDefinition dep : b.resolvedDependencies) {
				if (dep != null) {
					incoming.get(b).add(dep);
				}
			}
			if (b.configBeanDefinition != null) {
				incoming.get(b).add(b.configBeanDefinition);
			}
			// AOP interceptors must be instantiated before the beans they wrap
			if (b.needsProxy) {
				for (AptBeanDefinition interceptor : b.interceptors) {
					incoming.get(b).add(interceptor);
				}
			}
		}

		List<AptBeanDefinition> sorted = new ArrayList<>();
		Deque<AptBeanDefinition> queue = new ArrayDeque<>();

		// Seed with beans that have no incoming dependencies
		for (var entry : incoming.entrySet()) {
			if (entry.getValue().isEmpty()) {
				queue.add(entry.getKey());
			}
		}

		while (!queue.isEmpty()) {
			AptBeanDefinition current = queue.poll();
			sorted.add(current);

			for (var entry : incoming.entrySet()) {
				if (entry.getValue().remove(current) && entry.getValue().isEmpty()
						&& !sorted.contains(entry.getKey())) {
					queue.add(entry.getKey());
				}
			}
		}

		if (sorted.size() != beans.size()) {
			messager.printMessage(Diagnostic.Kind.ERROR, "Could not resolve all dependencies. Possible cycle among: "
					+ beans.stream().filter(b -> !sorted.contains(b)).map(AptBeanDefinition::qualifiedName).toList());
		}

		return sorted;
	}
}
