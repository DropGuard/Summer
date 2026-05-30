package summer.scanner.runtime;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
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
	private final Map<Class<?>, Object> singletons = new java.util.concurrent.ConcurrentHashMap<>();
	private final List<AutoCloseable> closeables = new ArrayList<>();
	private RuntimeBeanFactory beanFactory;

	public RuntimeApplicationContext() {
		this.componentScanner = new ComponentScanner();
		this.dependencyGraph = new DependencyGraph();
	}

	RuntimeApplicationContext scan(String... userPackages) {
		this.componentScanner.scan(userPackages);
		this.initializeBeans();
		return this;
	}

	void registerComponent(Class<?> clazz) {
		componentScanner.registerComponent(clazz);
	}

	public void initializeBeans() {
		Set<Class<?>> replacedConfigs = componentScanner.resolveReplacements();

		beanFactory = new RuntimeBeanFactory(singletons, closeables, dependencyGraph, this);

		for (Class<?> clazz : componentScanner.getComponentClasses()) {
			if (clazz.isAnnotationPresent(Configuration.class)) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Bean.class)) {
						beanFactory.beanProducers().put(method.getReturnType(), method);
					}
				}
			}
		}

		RuntimeConditionEvaluator.evaluate(componentScanner.getComponentClasses(), beanFactory.beanProducers());

		dependencyGraph.buildGraph(componentScanner.getComponentClasses());

		if (dependencyGraph.hasCircularDependencies()) {
			throw new CircularDependencyException("Circular dependencies detected");
		}

		List<Class<?>> instantiationOrder = dependencyGraph.topologicalSort();

		for (Class<?> clazz : instantiationOrder) {
			beanFactory.instantiateBean(clazz);
		}

		beanFactory.applyAopPass();
	}

	private Method findBeanProducer(Class<?> type) {
		Map<Class<?>, Method> producers = beanFactory.beanProducers();
		Method direct = producers.get(type);
		if (direct != null)
			return direct;
		for (var entry : producers.entrySet()) {
			if (type.isAssignableFrom(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> type) {
		Object instance = singletons.get(type);
		if (instance != null) {
			return (T) instance;
		}


		if (componentScanner.getComponentClasses().contains(type)) {
			return (T) beanFactory.instantiateBean(type);
		}

		// Not yet instantiated — find implementation class
		List<Class<?>> implementingClasses = componentScanner.getComponentClasses().stream()
				.filter(clazz -> type.isAssignableFrom(clazz) && !clazz.isInterface()).collect(Collectors.toList());

		if (!implementingClasses.isEmpty()) {
			if (implementingClasses.size() > 1) {
				throw new AmbiguousBeanException(
						"Ambiguous dependency. Multiple beans found for type: " + type.getName());
			}
			return (T) getBean(implementingClasses.get(0));
		}

		Method producer = findBeanProducer(type);
		if (producer != null) {
			return (T) beanFactory.invokeBeanProducer(producer, producer.getReturnType());
		}

		throw new NoSuchBeanException("No bean found of type: " + type.getName());
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> getBeansOfType(Class<T> type) {
		List<T> result = new ArrayList<>();
		for (Map.Entry<Class<?>, Object> entry : singletons.entrySet()) {
			if (type.isAssignableFrom(entry.getKey()) && !result.contains(entry.getValue())) {
				result.add((T) entry.getValue());
			}
		}
		for (Class<?> clazz : componentScanner.getComponentClasses()) {
			if (singletons.containsKey(clazz))
				continue;
			if (type.isAssignableFrom(clazz) && !clazz.isInterface()) {
				if (beanFactory.isInstantiating(clazz)) {
					continue;
				}
				try {
					result.add((T) getBean(clazz));
				} catch (NoSuchBeanException ignored) {
				}
			}
		}
		return result;
	}

	@Override
	public Set<Class<?>> getComponentClasses() {
		return Collections.unmodifiableSet(componentScanner.getComponentClasses());
	}

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
