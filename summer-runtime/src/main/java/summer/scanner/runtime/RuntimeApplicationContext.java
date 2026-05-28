package summer.scanner.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.aop.MethodInterceptor;
import summer.aop.ProxyFactory;
import summer.core.ApplicationContext;
import summer.core.Provider;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.BeanCreationException;
import summer.core.exception.CircularDependencyException;
import summer.core.exception.NoSuchBeanException;

/**
 * The runtime Summer application context that manages beans and their
 * dependencies using ClassGraph and Reflection.
 */
public class RuntimeApplicationContext implements ApplicationContext {

	private static final Logger log = LoggerFactory.getLogger(RuntimeApplicationContext.class);

	private final ComponentScanner componentScanner;
	private final DependencyGraph dependencyGraph;
	private final Map<Class<?>, Object> singletons = new java.util.concurrent.ConcurrentHashMap<>();
	/**
	 * Tracks AutoCloseable singletons in instantiation order for reverse-order
	 * shutdown.
	 */
	private final List<AutoCloseable> closeables = new ArrayList<>();
	/** Maps produced types to their @Bean factory methods. */
	private final Map<Class<?>, Method> beanProducers = new java.util.concurrent.ConcurrentHashMap<>();
	/**
	 * Caches produced bean instances to avoid calling @Bean methods multiple times.
	 */
	private final Map<Class<?>, Object> producedSingletons = new java.util.concurrent.ConcurrentHashMap<>();
	/** Classes currently being instantiated (prevents reentrant instantiation). */
	private final Set<Class<?>> currentlyInstantiating = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public RuntimeApplicationContext() {
		this.componentScanner = new ComponentScanner();
		this.dependencyGraph = new DependencyGraph();
	}

	/**
	 * Scans for @Component annotated classes and initializes the context. All
	 * modules contribute beans via pre-built {@code META-INF/jandex.idx} files;
	 * user packages without an index are scanned on-the-fly as a development
	 * fallback.
	 */
	RuntimeApplicationContext scan(String... userPackages) {
		this.componentScanner.scan(userPackages);
		this.initializeBeans();
		return this;
	}

	/**
	 * Register a component class directly with the context. Internal use only —
	 * prefer classpath scanning via {@link #scan()}.
	 */
	void registerComponent(Class<?> clazz) {
		componentScanner.registerComponent(clazz);
	}

	/**
	 * Initialize all beans by resolving their dependencies and instantiating them.
	 */
	public void initializeBeans() {
		// Resolve @Replacements first: remove replaced @Configuration classes
		Set<Class<?>> replacedConfigs = componentScanner.resolveReplacements();

		// Index @Bean factory methods from @Configuration classes (before
		// instantiation)
		// Skip @Bean methods from replaced configuration classes
		for (Class<?> clazz : componentScanner.getComponentClasses()) {
			if (clazz.isAnnotationPresent(Configuration.class)) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Bean.class)) {
						beanProducers.put(method.getReturnType(), method);
					}
				}
			}
		}

		// Evaluate @ConditionalOnBean: remove components whose conditions are not met
		evaluateConditions();

		// Build dependency graph
		dependencyGraph.buildGraph(componentScanner.getComponentClasses());

		// Check for circular dependencies
		if (dependencyGraph.hasCircularDependencies()) {
			throw new CircularDependencyException("Circular dependencies detected");
		}

		// Sort beans by dependency order
		List<Class<?>> instantiationOrder = dependencyGraph.topologicalSort();

		// Instantiate beans
		for (Class<?> clazz : instantiationOrder) {
			instantiateBean(clazz);
		}
	}

	/**
	 * Removes components whose {@code @ConditionalOnBean} conditions are not met.
	 * Iterates until stable, because removing one component may un-satisfy
	 * another's condition.
	 */
	private void evaluateConditions() {
		boolean changed = true;
		while (changed) {
			changed = false;
			Set<Class<?>> components = componentScanner.getComponentClasses();
			for (Class<?> clazz : new ArrayList<>(components)) {
				summer.core.annotation.ConditionalOnBean cond = clazz
						.getAnnotation(summer.core.annotation.ConditionalOnBean.class);
				if (cond == null)
					continue;
				Class<?> requiredType = cond.value();
				// Check component classes
				boolean satisfied = components.stream()
						.anyMatch(c -> requiredType.isAssignableFrom(c) && !c.isInterface());
				// Also check @Bean producers
				if (!satisfied) {
					satisfied = beanProducers.containsKey(requiredType) || beanProducers.entrySet().stream()
							.anyMatch(e -> requiredType.isAssignableFrom(e.getKey()));
				}
				if (!satisfied) {
					components.remove(clazz);
					changed = true;
				}
			}
		}
	}

	private Object instantiateBean(Class<?> clazz) {
		if (singletons.containsKey(clazz)) {
			return singletons.get(clazz);
		}
		if (currentlyInstantiating.contains(clazz)) {
			throw new CircularDependencyException("Reentrant bean instantiation detected for: " + clazz.getName());
		}
		currentlyInstantiating.add(clazz);

		try {
			Constructor<?> constructor = dependencyGraph.getConstructorForClass(clazz);
			// Resolve dependencies
			Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(paramType -> {
				if (paramType == ApplicationContext.class) {
					return this;
				}
				return getBean(paramType);
			}).toArray();

			Object instance = constructor.newInstance(dependencies);

			// Handle Provider pattern
			if (instance instanceof Provider<?> provider) {
				Object providedInstance = provider.provide();
				Class<?> providedType = getProvidedType(clazz);
				if (providedInstance instanceof AutoCloseable closeable) {
					closeables.add(closeable);
				}
				singletons.put(providedType, providedInstance);
				singletons.put(clazz, instance);
				currentlyInstantiating.remove(clazz);
				return providedInstance;
			}

			// Track plain AutoCloseable beans for lifecycle management
			if (instance instanceof AutoCloseable closeable) {
				closeables.add(closeable);
			}
			// Store raw instance first so AOP interceptor lookup doesn't re-enter
			singletons.put(clazz, instance);

			// Apply AOP proxies if interceptors are present and target has interfaces
			if (instance.getClass().getInterfaces().length > 0 && !(instance instanceof MethodInterceptor)) {
				List<MethodInterceptor> interceptors = getBeansOfType(MethodInterceptor.class).stream()
						.filter(Objects::nonNull).filter(interceptor -> interceptor.supports(clazz))
						.collect(Collectors.toList());

				if (!interceptors.isEmpty()) {
					Object proxy = ProxyFactory.createProxy(instance, interceptors);
					// JDK dynamic proxies only implement interfaces, not the concrete class.
					// Store the proxy under each interface key so interface-based lookups
					// (e.g. getBean(WebSocketBroadcaster.class)) return the proxied bean.
					// Keep the raw instance under the concrete class key so that direct
					// lookups by concrete type still work without a ClassCastException.
					for (Class<?> iface : clazz.getInterfaces()) {
						singletons.put(iface, proxy);
					}
					// The concrete key (already stored on line above) retains the raw instance,
					// which IS the correct concrete type and won't cause a ClassCastException.
				}
			}

			currentlyInstantiating.remove(clazz);
			return instance;
		} catch (NoSuchBeanException e) {
			currentlyInstantiating.remove(clazz);
			throw e;
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			currentlyInstantiating.remove(clazz);
			throw new BeanCreationException("Failed to instantiate bean: " + clazz.getName(), e);
		}
	}

	/**
	 * Finds an @Bean producer whose return type is assignable to the requested
	 * type.
	 */
	private Method findBeanProducer(Class<?> type) {
		// Direct match first
		Method direct = beanProducers.get(type);
		if (direct != null)
			return direct;
		// Assignability match (e.g. DataSource <- HikariDataSource)
		for (var entry : beanProducers.entrySet()) {
			if (type.isAssignableFrom(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Invokes an @Bean factory method, resolving its parameters via getBean().
	 */
	private Object invokeBeanProducer(Method producer, Class<?> producedType) {
		try {
			Class<?> configClass = producer.getDeclaringClass();
			Object configBean = singletons.get(configClass);
			// Ensure the @Configuration bean is instantiated
			if (configBean == null && componentScanner.getComponentClasses().contains(configClass)) {
				configBean = instantiateBean(configClass);
			}
			if (configBean == null) {
				throw new BeanCreationException("Configuration bean not instantiated: " + configClass.getName());
			}
			Object[] args = Arrays.stream(producer.getParameterTypes()).map(this::getBean).toArray();
			Object result = producer.invoke(configBean, args);
			producedSingletons.put(producedType, result);
			if (result instanceof AutoCloseable closeable) {
				closeables.add(closeable);
			}
			return result;
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new BeanCreationException("Failed to invoke @Bean method: " + producer.getName(), e);
		}
	}

	private Class<?> getProvidedType(Class<?> providerClass) {
		for (java.lang.reflect.Type iface : providerClass.getGenericInterfaces()) {
			if (iface instanceof java.lang.reflect.ParameterizedType pt) {
				if (pt.getRawType() == Provider.class) {
					return (Class<?>) pt.getActualTypeArguments()[0];
				}
			}
		}
		throw new BeanCreationException("Could not determine provided type for: " + providerClass.getName());
	}

	/**
	 * Gets a bean instance of the given type.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> type) {
		Object instance = singletons.get(type);
		if (instance != null) {
			return (T) instance;
		}

		// Check if this type is a component
		if (componentScanner.getComponentClasses().contains(type)) {
			return (T) instantiateBean(type);
		}

		// Look for a component that implements the interface
		List<Class<?>> implementingClasses = componentScanner.getComponentClasses().stream()
				.filter(clazz -> type.isAssignableFrom(clazz) && !clazz.isInterface()).collect(Collectors.toList());

		if (!implementingClasses.isEmpty()) {
			if (implementingClasses.size() > 1) {
				throw new AmbiguousBeanException(
						"Ambiguous dependency. Multiple beans found for type: " + type.getName());
			}
			Class<?> selectedClass = implementingClasses.get(0);
			return (T) getBean(selectedClass);
		}

		// Check @Bean factory methods
		Object produced = producedSingletons.get(type);
		if (produced != null) {
			return (T) produced;
		}
		// Assignability match in produced singletons (e.g. DataSource <-
		// HikariDataSource)
		for (var entry : producedSingletons.entrySet()) {
			if (type.isAssignableFrom(entry.getKey())) {
				return (T) entry.getValue();
			}
		}
		Method producer = findBeanProducer(type);
		if (producer != null) {
			return (T) invokeBeanProducer(producer, producer.getReturnType());
		}

		throw new NoSuchBeanException("No bean found of type: " + type.getName());
	}

	/**
	 * Gets all bean instances that are assignable to the given type.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> getBeansOfType(Class<T> type) {
		List<T> result = new ArrayList<>();
		// From component classes
		for (Class<?> clazz : componentScanner.getComponentClasses()) {
			if (type.isAssignableFrom(clazz) && !clazz.isInterface()) {
				if (currentlyInstantiating.contains(clazz)) {
					continue;
				}
				try {
					result.add((T) getBean(clazz));
				} catch (NoSuchBeanException ignored) {
					// Module component with unresolvable dependencies — skip
				}
			}
		}
		// From @Bean produced singletons
		for (Map.Entry<Class<?>, Object> entry : producedSingletons.entrySet()) {
			if (type.isAssignableFrom(entry.getKey()) && !result.contains(entry.getValue())) {
				result.add((T) entry.getValue());
			}
		}
		return result;
	}

	/**
	 * Gets all registered component classes.
	 */
	@Override
	public Set<Class<?>> getComponentClasses() {
		return Collections.unmodifiableSet(componentScanner.getComponentClasses());
	}

	/**
	 * Closes all AutoCloseable singletons in reverse instantiation order. Errors
	 * during individual close() calls are logged but do not abort the shutdown
	 * sequence.
	 */
	@Override
	public void destroy() {
		List<AutoCloseable> reversed = new ArrayList<>(closeables);
		Collections.reverse(reversed);
		for (AutoCloseable closeable : reversed) {
			try {
				closeable.close();
				log.debug("[Summer] Closed: {}", closeable.getClass().getSimpleName());
			} catch (Exception e) {
				log.warn("[Summer] Error closing resource: {}", closeable.getClass().getSimpleName(), e);
			}
		}
	}

}
