package summer.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import summer.core.exception.CircularDependencyException;
import summer.core.exception.NoSuchBeanException;

/**
 * Maven plugin dependency resolver. Uses qualified name strings for type
 * matching.
 */
public final class DependencyResolver {

	public List<BeanDefinition> resolve(List<BeanDefinition> beans) {
		for (BeanDefinition bean : beans) {
			resolveDependencies(bean, beans);
		}

		for (BeanDefinition bean : beans) {
			if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT) {
				linkConfigBean(bean, beans);
			}
		}

		if (hasCycle(beans)) {
			throw new CircularDependencyException("Circular dependency detected");
		}

		return topologicalSort(beans);
	}

	private void resolveDependencies(BeanDefinition bean, List<BeanDefinition> allBeans) {
		List<String> paramTypes = bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT
				? bean.producerParamTypes
				: bean.constructorParamTypes;

		bean.resolvedDependencies.clear();
		for (int i = 0; i < paramTypes.size(); i++) {
			String paramType = paramTypes.get(i);
			if (paramType.equals("summer.core.ApplicationContext"))
				continue;

			// Handle List<T> parameters - collect all beans of type T
			if (paramType.equals("java.util.List") && bean.listElementTypes.containsKey(i)) {
				String elementType = bean.listElementTypes.get(i);
				List<BeanDefinition> matches = findAllBeans(elementType, allBeans);
				// Add all matches as resolved dependencies (they'll be used to generate a List)
				bean.resolvedDependencies.addAll(matches);
				continue;
			}

			BeanDefinition resolved = findBean(paramType, allBeans);
			if (resolved == null) {
				throw new NoSuchBeanException(
						"No bean found for dependency type: " + paramType + " required by " + bean.qualifiedName);
			}
			bean.resolvedDependencies.add(resolved);
		}
	}

	private List<BeanDefinition> findAllBeans(String paramType, List<BeanDefinition> allBeans) {
		List<BeanDefinition> matches = new ArrayList<>();
		for (BeanDefinition candidate : allBeans) {
			if (candidate.qualifiedName.equals(paramType) || candidate.interfaceNames.contains(paramType)) {
				matches.add(candidate);
			}
		}
		return matches;
	}

	private void linkConfigBean(BeanDefinition factoryProduct, List<BeanDefinition> allBeans) {
		for (BeanDefinition candidate : allBeans) {
			if (candidate.kind == BeanDefinition.Kind.CONFIGURATION
					&& candidate.qualifiedName.equals(factoryProduct.configClassName)) {
				factoryProduct.configBeanDefinition = candidate;
				return;
			}
		}
		throw new NoSuchBeanException(
				"Could not find @Configuration bean for factory product: " + factoryProduct.qualifiedName);
	}

	BeanDefinition findBean(String paramType, List<BeanDefinition> allBeans) {
		for (BeanDefinition candidate : allBeans) {
			if (candidate.qualifiedName.equals(paramType))
				return candidate;
		}

		List<BeanDefinition> matches = new ArrayList<>();
		for (BeanDefinition candidate : allBeans) {
			if (candidate.interfaceNames.contains(paramType))
				matches.add(candidate);
		}
		return matches.size() == 1 ? matches.get(0) : null;
	}

	private boolean hasCycle(List<BeanDefinition> beans) {
		Set<BeanDefinition> visited = new HashSet<>();
		Set<BeanDefinition> stack = new HashSet<>();
		for (BeanDefinition bean : beans) {
			if (hasCycleDfs(bean, visited, stack))
				return true;
		}
		return false;
	}

	private boolean hasCycleDfs(BeanDefinition bean, Set<BeanDefinition> visited, Set<BeanDefinition> stack) {
		if (stack.contains(bean))
			return true;
		if (visited.contains(bean))
			return false;

		visited.add(bean);
		stack.add(bean);

		for (BeanDefinition dep : bean.resolvedDependencies) {
			if (dep != null && hasCycleDfs(dep, visited, stack))
				return true;
		}
		if (bean.configBeanDefinition != null) {
			if (hasCycleDfs(bean.configBeanDefinition, visited, stack))
				return true;
		}
		if (bean.needsProxy) {
			for (BeanDefinition interceptor : bean.interceptors) {
				if (hasCycleDfs(interceptor, visited, stack))
					return true;
			}
		}

		stack.remove(bean);
		return false;
	}

	private List<BeanDefinition> topologicalSort(List<BeanDefinition> beans) {
		Map<BeanDefinition, Set<BeanDefinition>> incoming = buildIncomingEdges(beans);

		List<BeanDefinition> sorted = new ArrayList<>();
		Deque<BeanDefinition> queue = new ArrayDeque<>();
		for (var entry : incoming.entrySet()) {
			if (entry.getValue().isEmpty())
				queue.add(entry.getKey());
		}

		while (!queue.isEmpty()) {
			BeanDefinition current = queue.poll();
			sorted.add(current);
			for (var entry : incoming.entrySet()) {
				if (entry.getValue().remove(current) && entry.getValue().isEmpty() && !sorted.contains(entry.getKey()))
					queue.add(entry.getKey());
			}
		}

		if (sorted.size() != beans.size()) {
			throw new CircularDependencyException(
					"Could not resolve all dependencies. Possible cycle among: "
							+ beans.stream().filter(b -> !sorted.contains(b)).map(b -> b.qualifiedName).toList());
		}
		return sorted;
	}

	private Map<BeanDefinition, Set<BeanDefinition>> buildIncomingEdges(List<BeanDefinition> beans) {
		Map<BeanDefinition, Set<BeanDefinition>> incoming = new LinkedHashMap<>();
		for (BeanDefinition b : beans)
			incoming.put(b, new LinkedHashSet<>());

		for (BeanDefinition b : beans) {
			for (BeanDefinition dep : b.resolvedDependencies) {
				if (dep != null)
					incoming.get(b).add(dep);
			}
			if (b.configBeanDefinition != null)
				incoming.get(b).add(b.configBeanDefinition);
			if (b.needsProxy) {
				for (BeanDefinition interceptor : b.interceptors)
					incoming.get(b).add(interceptor);
			}
		}
		return incoming;
	}
}
