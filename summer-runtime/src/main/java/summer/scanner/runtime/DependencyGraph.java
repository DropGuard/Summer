package summer.scanner.runtime;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;
import summer.core.ApplicationContext;
import summer.core.ErrorCode;
import summer.core.exception.BeanCreationException;

/**
 * Dependency graph manager that handles dependency resolution and cycle
 * detection.
 */
public class DependencyGraph {

	private final Map<Class<?>, Set<Class<?>>> graph = new HashMap<>();
	private final Map<Class<?>, Constructor<?>> beanConstructors = new HashMap<>();

	/**
	 * Builds the dependency graph from component classes.
	 */
	public void buildGraph(Set<Class<?>> componentClasses) {
		for (Class<?> clazz : componentClasses) {
			Set<Class<?>> dependencies = getDependencies(clazz, componentClasses);
			graph.put(clazz, dependencies);
		}
	}

	private Set<Class<?>> getDependencies(Class<?> clazz, Set<Class<?>> componentClasses) {
		Constructor<?> constructor = getConstructor(clazz);
		return Arrays.stream(constructor.getParameterTypes()).filter(paramType -> paramType != ApplicationContext.class)
				.map(paramType -> resolveDependency(paramType, componentClasses)).filter(Objects::nonNull)
				.collect(Collectors.toSet());
	}

	private Class<?> resolveDependency(Class<?> paramType, Set<Class<?>> componentClasses) {
		List<Class<?>> matches = componentClasses.stream()
				.filter(componentClass -> paramType.isAssignableFrom(componentClass) && !componentClass.isInterface())
				.toList();

		if (matches.isEmpty()) {
			return null;
		}
		if (matches.size() == 1) {
			return matches.getFirst();
		}

		// Multiple implementations found - ambiguous dependency
		throw new summer.core.exception.AmbiguousBeanException("Ambiguous dependency. Multiple beans found for type: "
				+ paramType.getName() + ". Found: " + matches.stream().map(Class::getName).toList());
	}

	private Constructor<?> getConstructor(Class<?> clazz) {
		return beanConstructors.computeIfAbsent(clazz, this::findConstructor);
	}

	private Constructor<?> findConstructor(Class<?> clazz) {
		Constructor<?>[] constructors = clazz.getConstructors();
		if (constructors.length != 1) {
			throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED, "Component " + clazz.getName()
					+ " must have exactly ONE public constructor. Found: " + constructors.length);
		}
		return constructors[0];
	}

	/**
	 * Checks if the dependency graph has circular dependencies.
	 */
	public boolean hasCircularDependencies() {
		Set<Class<?>> visited = new HashSet<>();
		Set<Class<?>> recursionStack = new HashSet<>();

		for (Class<?> clazz : graph.keySet()) {
			if (hasCycle(clazz, visited, recursionStack)) {
				return true;
			}
		}

		return false;
	}

	private boolean hasCycle(Class<?> clazz, Set<Class<?>> visited, Set<Class<?>> recursionStack) {
		if (!visited.contains(clazz)) {
			visited.add(clazz);
			recursionStack.add(clazz);

			Set<Class<?>> dependencies = graph.getOrDefault(clazz, Collections.emptySet());
			for (Class<?> dependency : dependencies) {
				if (!visited.contains(dependency)) {
					if (hasCycle(dependency, visited, recursionStack)) {
						return true;
					}
				} else if (recursionStack.contains(dependency)) {
					return true;
				}
			}
		}

		recursionStack.remove(clazz);
		return false;
	}

	/**
	 * Performs topological sort on the dependency graph to determine instantiation
	 * order.
	 */
	public List<Class<?>> topologicalSort() {
		List<Class<?>> sorted = new ArrayList<>();
		Set<Class<?>> visited = new HashSet<>();

		for (Class<?> clazz : graph.keySet()) {
			if (!visited.contains(clazz)) {
				topologicalSortUtil(clazz, visited, sorted);
			}
		}

		return sorted;
	}

	private void topologicalSortUtil(Class<?> clazz, Set<Class<?>> visited, List<Class<?>> sorted) {
		visited.add(clazz);

		Set<Class<?>> dependencies = graph.getOrDefault(clazz, Collections.emptySet());
		for (Class<?> dependency : dependencies) {
			if (!visited.contains(dependency)) {
				topologicalSortUtil(dependency, visited, sorted);
			}
		}

		sorted.add(clazz);
	}

	/**
	 * Gets the dependency graph.
	 */
	public Map<Class<?>, Set<Class<?>>> getGraph() {
		return graph;
	}

	/**
	 * Gets the constructor for a given class.
	 */
	public Constructor<?> getConstructorForClass(Class<?> clazz) {
		return getConstructor(clazz);
	}
}