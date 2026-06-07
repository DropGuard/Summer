package summer.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.core.Engine;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.CircularDependencyException;
import summer.core.exception.NoSuchBeanException;

/**
 * The runtime Summer application context that manages beans and their
 * dependencies using Jandex and Reflection.
 */
public class RuntimeApplicationContext implements ApplicationContext {

	private static final Logger log = LoggerFactory.getLogger(RuntimeApplicationContext.class);

	private final ComponentScanner componentScanner;
	private final DependencyGraph dependencyGraph;
	private final Map<Class<?>, Object> singletons = new java.util.LinkedHashMap<>();
	private final List<AutoCloseable> closeables = new ArrayList<>();
	private List<Object> instantiationOrder = List.of();
	private RuntimeBeanFactory beanFactory;

	public RuntimeApplicationContext() {
		this.componentScanner = new ComponentScanner();
		this.dependencyGraph = new DependencyGraph();
	}

	/**
	 * Convenience factory method that creates a RuntimeApplicationContext and scans
	 * from the entry point's package.
	 *
	 * <p>
	 * Registers {@link RuntimeDiMarker} as a singleton before scanning, enabling
	 * {@code @ConditionalOnBean(RuntimeDiMarker.class)} on runtime-specific
	 * configurations.
	 * </p>
	 */
	public static RuntimeApplicationContext create(Class<?> entryPoint) {
		RuntimeApplicationContext ctx = new RuntimeApplicationContext();
		ctx.registerSingleton(summer.core.RuntimeDiMarker.class, new summer.core.RuntimeDiMarker());
		return ctx.scan(entryPoint.getPackageName());
	}

	@Override
	public Engine engine() {
		return Engine.RUNTIME;
	}

	public RuntimeApplicationContext scan(String... userPackages) {
		this.componentScanner.scan(userPackages);
		this.initializeBeans();
		return this;
	}

	public void registerComponent(Class<?> clazz) {
		componentScanner.registerComponent(clazz);
	}

	public void registerSingleton(Class<?> type, Object instance) {
		singletons.put(type, instance);
	}

	public void initializeBeans() {
		beanFactory = new RuntimeBeanFactory(singletons, closeables, dependencyGraph, this);

		// 0. Bind @ConfigurationProperties records from YAML
		bindConfigurationProperties();

		Set<Object> allNodes = new HashSet<>(componentScanner.getComponentClasses());

		// Include programmatically registered singletons in conditional evaluation
		allNodes.addAll(singletons.keySet());

		for (Class<?> clazz : componentScanner.getComponentClasses()) {
			if (clazz.isAnnotationPresent(Configuration.class)) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Bean.class)) {
						allNodes.add(method);
					}
				}
			}
		}

		// 1. Evaluate @ConditionalOnBean and @Replaces
		// Both are handled by RuntimeConditionEvaluator.evaluate()
		RuntimeConditionEvaluator.evaluate(allNodes);
		componentScanner.getComponentClasses().removeIf(clazz -> !allNodes.contains(clazz));

		// 2. Build Dependency Graph
		dependencyGraph.buildGraph(allNodes);

		if (dependencyGraph.hasCircularDependencies()) {
			throw new CircularDependencyException("Circular dependencies detected");
		}

		// 4. Topological Sort and Instantiation
		instantiationOrder = dependencyGraph.topologicalSort();

		for (Object node : instantiationOrder) {
			if (node instanceof Class<?> clazz) {
				beanFactory.instantiateBean(clazz);
			} else if (node instanceof Method method) {
				beanFactory.invokeBeanProducer(method);
			}
		}

	}

	/**
	 * Scans for {@code @ConfigurationProperties}-annotated records and binds them
	 * from {@code application.yml}. Results are registered as singletons so they
	 * are available as dependencies for other beans.
	 *
	 * <p>
	 * Fields absent from YAML are left as {@code null}. Skips types already
	 * registered (e.g. via a manual {@code @Bean} method).
	 * </p>
	 */
	private void bindConfigurationProperties() {
		List<Class<?>> configClasses = componentScanner.discoverConfigurationProperties();
		if (configClasses.isEmpty()) {
			return;
		}

		ConfigurationLoader loader = new ConfigurationLoader();
		for (Class<?> configClass : configClasses) {
			if (singletons.containsKey(configClass)) {
				continue; // already registered (e.g. by @Bean)
			}
			ConfigurationProperties ann = configClass.getAnnotation(ConfigurationProperties.class);
			if (ann == null)
				continue;

			String prefix = ann.prefix();
			Object instance = prefix.isEmpty()
					? loader.bind("application.yml", configClass)
					: loader.bind("application.yml", configClass, prefix);
			singletons.put(configClass, instance);
			log.debug("[Summer] Bound @ConfigurationProperties: {} (prefix='{}')", configClass.getSimpleName(), prefix);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> type) {
		Object instance = singletons.get(type);
		if (instance != null) {
			return (T) instance;
		}

		List<Object> matches = new ArrayList<>();
		for (Object singleton : singletons.values()) {
			if (type.isInstance(singleton) && !matches.contains(singleton)) {
				matches.add(singleton);
			}
		}

		if (matches.isEmpty()) {
			throw new NoSuchBeanException("No bean found of type: " + type.getName());
		}
		if (matches.size() == 1) {
			return (T) matches.get(0);
		}
		throw new AmbiguousBeanException("Ambiguous dependency. Multiple beans found for type: " + type.getName());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> getBeans(Class<T> type) {
		List<T> result = new ArrayList<>();
		for (Object instance : singletons.values()) {
			if (instance != null && type.isInstance(instance) && !result.contains(instance)) {
				result.add((T) instance);
			}
		}
		return result;
	}

	public boolean containsBean(Class<?> type) {
		// Check exact match
		if (singletons.containsKey(type)) {
			return true;
		}
		// Check type compatibility
		for (Object instance : singletons.values()) {
			if (instance != null && type.isInstance(instance)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Set<Class<?>> getRegisteredTypes() {
		return Collections.unmodifiableSet(componentScanner.getComponentClasses());
	}

	@Override
	public void close() {
		for (AutoCloseable closeable : closeables.reversed()) {
			try {
				closeable.close();
				log.debug("[Summer] Closed: {}", closeable.getClass().getSimpleName());
			} catch (Exception e) {
				log.warn("[Summer] Error closing resource: {}", closeable.getClass().getSimpleName(), e);
			}
		}
	}
}
