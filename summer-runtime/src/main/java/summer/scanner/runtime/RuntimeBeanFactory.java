package summer.scanner.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import summer.aop.MethodInterceptor;
import summer.core.ApplicationContext;
import summer.core.Provider;
import summer.core.exception.BeanCreationException;
import summer.core.exception.CircularDependencyException;
import summer.core.exception.NoSuchBeanException;

class RuntimeBeanFactory {

	private final Map<Class<?>, Object> singletons;
	private final List<AutoCloseable> closeables;
	private final Set<Class<?>> currentlyInstantiating = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final Map<Class<?>, Method> beanProducers = new java.util.concurrent.ConcurrentHashMap<>();
	private final DependencyGraph dependencyGraph;
	private final ApplicationContext context;

	RuntimeBeanFactory(Map<Class<?>, Object> singletons, List<AutoCloseable> closeables,
			DependencyGraph dependencyGraph, ApplicationContext context) {
		this.singletons = singletons;
		this.closeables = closeables;
		this.dependencyGraph = dependencyGraph;
		this.context = context;
	}

	Map<Class<?>, Method> beanProducers() {
		return beanProducers;
	}

	boolean isInstantiating(Class<?> clazz) {
		return currentlyInstantiating.contains(clazz);
	}

	void applyAopPass() {
		List<MethodInterceptor> allInterceptors = context.getBeansOfType(MethodInterceptor.class);
		for (var entry : new java.util.LinkedHashMap<>(singletons).entrySet()) {
			Class<?> clazz = entry.getKey();
			if (clazz.isInterface())
				continue;
			Object instance = entry.getValue();
			if (instance instanceof MethodInterceptor)
				continue;
			Object result = RuntimeAopProcessor.applyProxy(instance, clazz, allInterceptors, singletons);
			if (result != instance) {
				singletons.put(clazz, instance); // concrete key keeps raw instance
			}
		}
	}

	Object instantiateBean(Class<?> clazz) {
		if (singletons.containsKey(clazz)) {
			return singletons.get(clazz);
		}
		if (currentlyInstantiating.contains(clazz)) {
			throw new CircularDependencyException("Reentrant bean instantiation detected for: " + clazz.getName());
		}
		currentlyInstantiating.add(clazz);

		try {
			Constructor<?> constructor = dependencyGraph.getConstructorForClass(clazz);
			Object[] dependencies = Arrays.stream(constructor.getParameterTypes()).map(paramType -> {
				if (paramType == ApplicationContext.class) {
					return context;
				}
				return context.getBean(paramType);
			}).toArray();

			Object instance = constructor.newInstance(dependencies);

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

			if (instance instanceof AutoCloseable closeable) {
				closeables.add(closeable);
			}
			singletons.put(clazz, instance);
			for (Class<?> iface : clazz.getInterfaces()) {
				singletons.putIfAbsent(iface, instance);
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

	Object invokeBeanProducer(Method producer, Class<?> producedType) {
		try {
			Class<?> configClass = producer.getDeclaringClass();
			Object configBean = singletons.get(configClass);
			if (configBean == null) {
				configBean = context.getBean(configClass);
			}
			if (configBean == null) {
				throw new BeanCreationException("Configuration bean not instantiated: " + configClass.getName());
			}
			Object[] args = Arrays.stream(producer.getParameterTypes()).map(context::getBean).toArray();
			Object result = producer.invoke(configBean, args);
			singletons.put(producedType, result);
			for (Class<?> iface : result.getClass().getInterfaces()) {
				singletons.putIfAbsent(iface, result);
			}
			if (result instanceof AutoCloseable closeable) {
				closeables.add(closeable);
			}
			return result;
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new BeanCreationException("Failed to invoke @Bean method: " + producer.getName(), e);
		}
	}

	private static Class<?> getProvidedType(Class<?> providerClass) {
		for (java.lang.reflect.Type iface : providerClass.getGenericInterfaces()) {
			if (iface instanceof java.lang.reflect.ParameterizedType pt) {
				if (pt.getRawType() == Provider.class) {
					return (Class<?>) pt.getActualTypeArguments()[0];
				}
			}
		}
		throw new BeanCreationException("Could not determine provided type for: " + providerClass.getName());
	}
}
