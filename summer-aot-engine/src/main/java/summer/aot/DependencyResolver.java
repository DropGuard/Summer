package summer.aot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.bean.BeanDefinition;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Maven plugin dependency resolver. Uses qualified name strings for type
 * matching.
 */
public final class DependencyResolver {

	private static final Logger log = LoggerFactory.getLogger(DependencyResolver.class);

	public List<BeanDefinition> resolve(List<BeanDefinition> beans) {
		validateUniqueBeanNames(beans);

		for (BeanDefinition bean : beans) {
			resolveDependencies(bean, beans);
		}

		for (BeanDefinition bean : beans) {
			if (bean.isFactoryMethod()) {
				linkConfigBean(bean, beans);
			}
		}

		return topologicalSort(beans);
	}

	private void validateUniqueBeanNames(List<BeanDefinition> beans) {
		Map<String, String> nameToSource = new LinkedHashMap<>();
		for (BeanDefinition bean : beans) {
			String existing = nameToSource.get(bean.qualifiedName);
			if (existing == null) {
				nameToSource.put(bean.qualifiedName,
						bean.isFactoryMethod()
								? bean.configClassName + "#" + bean.producerMethodName
								: bean.qualifiedName);
			} else if (!existing.equals(bean.isFactoryMethod()
					? bean.configClassName + "#" + bean.producerMethodName
					: bean.qualifiedName)) {
				throw new AmbiguousBeanException("Multiple beans found for type: " + bean.qualifiedName
						+ " is defined by both " + existing + " and "
						+ (bean.isFactoryMethod()
								? bean.configClassName + "#" + bean.producerMethodName
								: bean.qualifiedName));
			}
		}
	}

	private void resolveDependencies(BeanDefinition bean, List<BeanDefinition> allBeans) {
		if (bean instanceof summer.core.bean.ConfigPropertiesBean)
			return;

		List<String> paramTypes = bean.isFactoryMethod() ? bean.producerParamTypes : bean.constructorParamTypes;
		Map<Integer, String> listElementTypes = bean.isFactoryMethod() ? Map.of() : bean.listElementTypes;

		bean.resolvedDependencies.clear();
		for (int i = 0; i < paramTypes.size(); i++) {
			String paramType = paramTypes.get(i);
			if (paramType.equals("summer.core.BeanContainer"))
				continue;

			// Handle List<T> parameters - collect all beans of type T
			if (paramType.equals("java.util.List") && listElementTypes.containsKey(i)) {
				String elementType = listElementTypes.get(i);
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
			if (candidate.qualifiedName.equals(paramType)) {
				matches.add(candidate);
			} else if (candidate.interfaceNames.contains(paramType)) {
				matches.add(candidate);
			}
		}
		return matches;
	}

	private void linkConfigBean(BeanDefinition factoryProduct, List<BeanDefinition> allBeans) {
		for (BeanDefinition candidate : allBeans) {
			if (candidate.qualifiedName.equals(factoryProduct.configClassName)) {
				factoryProduct.configBeanDefinition = candidate;
				return;
			}
		}
		throw new NoSuchBeanException(
				"Could not find @Configuration bean for factory product: " + factoryProduct.qualifiedName);
	}

	BeanDefinition findBean(String paramType, List<BeanDefinition> allBeans) {
		List<BeanDefinition> exactMatches = new ArrayList<>();
		for (BeanDefinition candidate : allBeans) {
			if (candidate.qualifiedName.equals(paramType))
				exactMatches.add(candidate);
		}
		if (exactMatches.size() == 1)
			return exactMatches.get(0);
		if (exactMatches.size() > 1) {
			throw new AmbiguousBeanException("Multiple beans found for type: " + paramType + " -> "
					+ exactMatches.stream().map(b -> b.qualifiedName).toList());
		}

		List<BeanDefinition> interfaceMatches = new ArrayList<>();
		for (BeanDefinition candidate : allBeans) {
			if (candidate.interfaceNames.contains(paramType))
				interfaceMatches.add(candidate);
		}
		if (interfaceMatches.size() == 1)
			return interfaceMatches.get(0);
		if (interfaceMatches.size() > 1) {
			throw new AmbiguousBeanException("Multiple beans found for type: " + paramType + " -> "
					+ interfaceMatches.stream().map(b -> b.qualifiedName).toList());
		}
		return null;
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
		if (bean.needsProxy()) {
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
			List<BeanDefinition> circular = beans.stream().filter(b -> !sorted.contains(b)).toList();
			List<String> circularNames = circular.stream().map(b -> b.qualifiedName).toList();
			log.warn("[Summer] Skipping {} bean(s) with circular dependencies: {}", circular.size(), circularNames);
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
			if (b.needsProxy()) {
				for (BeanDefinition interceptor : b.interceptors)
					incoming.get(b).add(interceptor);
			}
		}
		return incoming;
	}
}
