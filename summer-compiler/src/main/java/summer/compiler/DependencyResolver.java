package summer.compiler;

import java.util.*;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/**
 * Compile-time dependency resolver that builds a dependency graph from
 * {@link BeanDefinition}s and produces a topologically sorted instantiation
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
	List<BeanDefinition> resolve(List<BeanDefinition> beans) {
		// 1. Resolve each bean's dependencies to concrete BeanDefinition references
		for (BeanDefinition bean : beans) {
			resolveDependencies(bean, beans);
		}

		// 2. Link FACTORY_PRODUCT beans to their @Configuration bean
		for (BeanDefinition bean : beans) {
			if (bean.kind() == BeanDefinition.Kind.FACTORY_PRODUCT) {
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

	private void resolveDependencies(BeanDefinition bean, List<BeanDefinition> allBeans) {
		List<String> paramTypes;
		if (bean.kind() == BeanDefinition.Kind.FACTORY_PRODUCT) {
			paramTypes = bean.producerParamTypes();
		} else {
			paramTypes = bean.constructorParamTypes();
		}

		bean.resolvedDependencies().clear();
		for (String paramType : paramTypes) {
			if (paramType.equals("summer.core.ApplicationContext")) {
				continue; // Self-injected dependency
			}
			BeanDefinition resolved = findBean(paramType, allBeans);
			if (resolved == null) {
				messager.printMessage(Diagnostic.Kind.ERROR,
						"No bean found for dependency type: " + paramType + " required by " + bean.qualifiedName());
			} else {
				bean.resolvedDependencies().add(resolved);
			}
		}
	}

	private void linkConfigBean(BeanDefinition factoryProduct, List<BeanDefinition> allBeans) {
		String configClassName = factoryProduct.configClassName();
		for (BeanDefinition candidate : allBeans) {
			if (candidate.kind() == BeanDefinition.Kind.CONFIGURATION
					&& candidate.qualifiedName().equals(configClassName)) {
				factoryProduct.setConfigBeanDefinition(candidate);
				return;
			}
		}
		messager.printMessage(Diagnostic.Kind.ERROR,
				"Could not find @Configuration bean for factory product: " + factoryProduct.qualifiedName());
	}

	/**
	 * Finds a bean whose type is assignable to the requested paramType. Exact match
	 * is preferred; interface → implementation match is used as fallback.
	 */
	BeanDefinition findBean(String paramType, List<BeanDefinition> allBeans) {
		// Pass 1: exact type match
		for (BeanDefinition candidate : allBeans) {
			if (candidate.qualifiedName().equals(paramType)) {
				return candidate;
			}
		}

		// Pass 2: assignability (interface → implementation, or subclass)
		List<BeanDefinition> matches = new ArrayList<>();
		for (BeanDefinition candidate : allBeans) {
			if (isAssignable(candidate, paramType)) {
				matches.add(candidate);
			}
		}

		if (matches.size() == 1) {
			return matches.get(0);
		}

		if (matches.size() > 1) {
			throw new summer.core.SummerException(summer.core.ErrorCode.AMBIGUOUS_BEAN,
					"Ambiguous dependency. Multiple beans found for type: " + paramType);
		}

		return null;
	}

	/**
	 * Checks if a candidate bean is assignable to the target type. Handles
	 * interface implementation and class inheritance.
	 */
	private boolean isAssignable(BeanDefinition candidate, String targetType) {
		// Check if candidate implements the target interface
		for (String iface : candidate.interfaceNames()) {
			if (iface.equals(targetType)) {
				return true;
			}
		}

		// Check if candidate extends the target class
		String superClass = candidate.superClassName();
		while (superClass != null && !superClass.equals("java.lang.Object")) {
			if (superClass.equals(targetType)) {
				return true;
			}
			// Walk up the hierarchy (we'd need to find the parent bean, but for simplicity
			// just check the class name chain)
			break; // TODO: full hierarchy walk if needed
		}

		return false;
	}

	// -----------------------------------------------------------------------
	// Cycle detection (DFS-based)
	// -----------------------------------------------------------------------

	private boolean hasCycle(List<BeanDefinition> beans) {
		Set<BeanDefinition> visited = new HashSet<>();
		Set<BeanDefinition> stack = new HashSet<>();

		for (BeanDefinition bean : beans) {
			if (hasCycleDfs(bean, visited, stack)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasCycleDfs(BeanDefinition bean, Set<BeanDefinition> visited, Set<BeanDefinition> stack) {
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

		for (BeanDefinition dep : bean.resolvedDependencies()) {
			if (dep != null && hasCycleDfs(dep, visited, stack)) {
				return true;
			}
		}
		// Also traverse config bean dependency for FACTORY_PRODUCT
		if (bean.configBeanDefinition() != null) {
			if (hasCycleDfs(bean.configBeanDefinition(), visited, stack)) {
				return true;
			}
		}
		// Also traverse AOP interceptor dependencies
		if (bean.needsProxy()) {
			for (BeanDefinition interceptor : bean.interceptors()) {
				if (hasCycleDfs(interceptor, visited, stack)) {
					return true;
				}
			}
		}

		stack.remove(bean);
		return false;
	}

	// -----------------------------------------------------------------------
	// Topological sort (Kahn's algorithm)
	// -----------------------------------------------------------------------

	private List<BeanDefinition> topologicalSort(List<BeanDefinition> beans) {
		// Build adjacency: bean → set of beans it depends on
		Map<BeanDefinition, Set<BeanDefinition>> incoming = new LinkedHashMap<>();
		for (BeanDefinition b : beans) {
			incoming.put(b, new LinkedHashSet<>());
		}
		for (BeanDefinition b : beans) {
			for (BeanDefinition dep : b.resolvedDependencies()) {
				if (dep != null) {
					incoming.get(b).add(dep);
				}
			}
			if (b.configBeanDefinition() != null) {
				incoming.get(b).add(b.configBeanDefinition());
			}
			// AOP interceptors must be instantiated before the beans they wrap
			if (b.needsProxy()) {
				for (BeanDefinition interceptor : b.interceptors()) {
					incoming.get(b).add(interceptor);
				}
			}
		}

		List<BeanDefinition> sorted = new ArrayList<>();
		Deque<BeanDefinition> queue = new ArrayDeque<>();

		// Seed with beans that have no incoming dependencies
		for (var entry : incoming.entrySet()) {
			if (entry.getValue().isEmpty()) {
				queue.add(entry.getKey());
			}
		}

		while (!queue.isEmpty()) {
			BeanDefinition current = queue.poll();
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
					+ beans.stream().filter(b -> !sorted.contains(b)).map(BeanDefinition::qualifiedName).toList());
		}

		return sorted;
	}
}
