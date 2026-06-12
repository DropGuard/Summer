package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import summer.core.ApplicationContext;
import summer.core.ErrorCode;
import summer.core.exception.BeanCreationException;

/**
 * Dependency graph manager that handles dependency resolution and cycle
 * detection.
 */
public class DependencyGraph {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DependencyGraph.class);

	private final Map<Object, Set<Object>> graph = new java.util.LinkedHashMap<>();
	private final Map<Class<?>, Constructor<?>> beanConstructors = new HashMap<>();
	private final Map<Class<?>, Set<Class<?>>> beanInterceptorMap = new java.util.LinkedHashMap<>();

	/**
	 * Builds the dependency graph from component classes and bean methods.
	 */
	public void buildGraph(Set<Object> nodes) {
		for (Object node : nodes) {
			// Skip interfaces — they cannot be instantiated and have no constructors
			if (node instanceof Class<?> c && c.isInterface()) {
				log.trace("[Summer] Skipping interface: {}", c.getName());
				continue;
			}
			log.debug("[Summer] Building graph for: {}", node);
			Set<Object> dependencies = getDependencies(node, nodes);
			graph.put(node, dependencies);
		}
	}

	private Set<Object> getDependencies(Object node, Set<Object> allNodes) {
		Set<Object> deps = new java.util.LinkedHashSet<>();

		if (node instanceof Method method) {
			// @Bean method depends on its declaring @Configuration class
			deps.add(method.getDeclaringClass());
			resolveParamDeps(method.getGenericParameterTypes(), allNodes, deps);
			return deps;
		}

		if (!(node instanceof Class<?> clazz)) {
			return deps;
		}

		// @ConfigurationProperties records have no bean dependencies —
		// they are bound from YAML by the bean factory
		if (clazz.isAnnotationPresent(summer.core.config.ConfigurationProperties.class)) {
			return deps;
		}
		Constructor<?> constructor = getConstructor(clazz);
		Type[] paramTypes = constructor.getGenericParameterTypes();
		log.debug("[Summer] getDependencies: {} params={}", clazz.getName(), paramTypes.length);

		resolveParamDeps(paramTypes, allNodes, deps);

		// Implicit AOP dependencies: a bean depends on its matching interceptors
		Class<?> providedType = getProvidedType(node);
		if (!providedType.isAnnotationPresent(summer.aop.Interceptor.class)) {
			Set<Class<?>> matchingInterceptors = new java.util.LinkedHashSet<>();
			for (Object interceptorNode : allNodes) {
				Class<?> interceptorType = getProvidedType(interceptorNode);
				if (interceptorType.isAnnotationPresent(summer.aop.Interceptor.class)) {
					if (hasMatchingBinding(interceptorType, providedType)) {
						deps.add(interceptorNode);
						matchingInterceptors.add(interceptorType);
					}
				}
			}
			if (!matchingInterceptors.isEmpty()) {
				beanInterceptorMap.put(providedType, matchingInterceptors);
			}
		}

		return deps;
	}

	private void resolveParamDeps(Type[] paramTypes, Set<Object> allNodes, Set<Object> deps) {
		for (Type paramType : paramTypes) {
			if (paramType == ApplicationContext.class) {
				continue;
			}
			if (paramType instanceof ParameterizedType pt && pt.getRawType() == List.class) {
				Type elementType = pt.getActualTypeArguments()[0];
				Class<?> elementClass = getRawClass(elementType);
				deps.addAll(findAllProviders(elementClass, allNodes));
			} else {
				Class<?> paramClass = getRawClass(paramType);
				deps.add(resolveDependency(paramClass, allNodes));
			}
		}
	}

	private boolean hasMatchingBinding(Class<?> interceptorClass, Class<?> targetClass) {
		for (Class<? extends java.lang.annotation.Annotation> binding : BindingMatcher.findBindings(interceptorClass)) {
			if (targetClass.isAnnotationPresent(binding)) {
				return true;
			}
			for (Method method : targetClass.getMethods()) {
				if (method.isAnnotationPresent(binding)) {
					return true;
				}
			}
		}
		return false;
	}

	private Class<?> getRawClass(Type type) {
		if (type instanceof Class<?> clazz)
			return clazz;
		if (type instanceof ParameterizedType pt)
			return (Class<?>) pt.getRawType();
		throw new IllegalArgumentException("Unsupported type: " + type);
	}

	private List<Object> findAllProviders(Class<?> paramType, Set<Object> allNodes) {
		return allNodes.stream().filter(n -> paramType.isAssignableFrom(getProvidedType(n)))
				.filter(n -> !(n instanceof Class<?> c && c.isInterface())).toList();
	}

	private Object resolveDependency(Class<?> paramType, Set<Object> allNodes) {
		List<Object> matches = findAllProviders(paramType, allNodes);

		if (matches.isEmpty()) {
			throw new summer.core.exception.NoSuchBeanException(
					"No bean found for dependency type: " + paramType.getName());
		}
		if (matches.size() == 1) {
			return matches.getFirst();
		}

		// Multiple implementations found - ambiguous dependency
		throw new summer.core.exception.AmbiguousBeanException(
				"Ambiguous dependency. Multiple beans found for type: " + paramType.getName() + ". Found: " + matches);
	}

	public Class<?> getProvidedType(Object node) {
		if (node instanceof Class<?> clazz) {
			return clazz;
		} else if (node instanceof Method method) {
			return method.getReturnType();
		}
		throw new IllegalArgumentException("Unknown node type: " + node.getClass());
	}

	/**
	 * Returns the interceptor classes that match the given bean class, as computed
	 * during {@link #buildGraph(Set)}. Returns an empty set if no interceptors
	 * match.
	 */
	public Set<Class<?>> getMatchingInterceptorClasses(Class<?> beanClass) {
		return beanInterceptorMap.getOrDefault(beanClass, Collections.emptySet());
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
		Set<Object> visited = new HashSet<>();
		Set<Object> recursionStack = new HashSet<>();

		for (Object node : graph.keySet()) {
			if (hasCycle(node, visited, recursionStack)) {
				return true;
			}
		}

		return false;
	}

	private boolean hasCycle(Object node, Set<Object> visited, Set<Object> recursionStack) {
		if (!visited.contains(node)) {
			visited.add(node);
			recursionStack.add(node);

			Set<Object> dependencies = graph.getOrDefault(node, Collections.emptySet());
			for (Object dependency : dependencies) {
				if (!visited.contains(dependency)) {
					if (hasCycle(dependency, visited, recursionStack)) {
						return true;
					}
				} else if (recursionStack.contains(dependency)) {
					return true;
				}
			}
		}

		recursionStack.remove(node);
		return false;
	}

	/**
	 * Performs topological sort on the dependency graph to determine instantiation
	 * order.
	 */
	public List<Object> topologicalSort() {
		List<Object> sorted = new ArrayList<>();
		Set<Object> visited = new HashSet<>();

		for (Object node : graph.keySet()) {
			if (!visited.contains(node)) {
				topologicalSortUtil(node, visited, sorted);
			}
		}

		return sorted;
	}

	private void topologicalSortUtil(Object node, Set<Object> visited, List<Object> sorted) {
		visited.add(node);

		Set<Object> dependencies = graph.getOrDefault(node, Collections.emptySet());
		for (Object dependency : dependencies) {
			if (!visited.contains(dependency)) {
				topologicalSortUtil(dependency, visited, sorted);
			}
		}

		sorted.add(node);
	}

	/**
	 * Gets the constructor for a given class.
	 */
	public Constructor<?> getConstructorForClass(Class<?> clazz) {
		return getConstructor(clazz);
	}
}
