package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final Map<Class<?>, Method> beanMethods = new java.util.concurrent.ConcurrentHashMap<>();
	private final DependencyGraph dependencyGraph;
	private final ApplicationContext context;

	RuntimeBeanFactory(Map<Class<?>, Object> singletons, List<AutoCloseable> closeables,
			DependencyGraph dependencyGraph, ApplicationContext context) {
		this.singletons = singletons;
		this.closeables = closeables;
		this.dependencyGraph = dependencyGraph;
		this.context = context;
	}

	Map<Class<?>, Method> beanMethods() {
		return beanMethods;
	}

	boolean isInstantiating(Class<?> clazz) {
		return currentlyInstantiating.contains(clazz);
	}

	void applyAopPass() {
		List<MethodInterceptor> allInterceptors = context.getBeans(MethodInterceptor.class);
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
			Object instance = createInstance(clazz);
			return registerBean(clazz, instance);
		} catch (Exception e) {
			if (e instanceof NoSuchBeanException nse)
				throw nse;
			throw new BeanCreationException("Failed to instantiate bean: " + clazz.getName(), e);
		} finally {
			currentlyInstantiating.remove(clazz);
		}
	}

	private Object createInstance(Class<?> clazz) throws ReflectiveOperationException {
		Constructor<?> constructor = dependencyGraph.getConstructorForClass(clazz);
		Object[] dependencies = resolveDependencies(constructor);
		return constructor.newInstance(dependencies);
	}

	private Object[] resolveDependencies(Constructor<?> constructor) {
		java.lang.reflect.Type[] genericTypes = constructor.getGenericParameterTypes();
		Class<?>[] paramTypes = constructor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];

		for (int i = 0; i < paramTypes.length; i++) {
			Class<?> paramType = paramTypes[i];
			java.lang.reflect.Type genericType = genericTypes[i];

			if (paramType == ApplicationContext.class) {
				args[i] = context;
			} else if (paramType == List.class && genericType instanceof java.lang.reflect.ParameterizedType pt) {
				java.lang.reflect.Type elementType = pt.getActualTypeArguments()[0];
				if (elementType instanceof Class<?> elementClass) {
					args[i] = context.getBeans(elementClass);
				} else {
					args[i] = context.getBean(paramType);
				}
			} else {
				args[i] = context.getBean(paramType);
			}
		}
		return args;
	}

	private Object registerBean(Class<?> clazz, Object instance) {
		if (instance instanceof Provider<?> provider) {
			return registerProvider(clazz, provider);
		}
		return registerRegularBean(clazz, instance);
	}

	private Object registerProvider(Class<?> clazz, Provider<?> provider) {
		Object providedInstance = provider.provide();
		Class<?> providedType = getProvidedType(clazz);
		trackCloseable(providedInstance);
		singletons.put(providedType, providedInstance);
		singletons.put(clazz, provider);
		return providedInstance;
	}

	private Object registerRegularBean(Class<?> clazz, Object instance) {
		trackCloseable(instance);
		singletons.put(clazz, instance);
		registerAllInterfaces(clazz, instance);
		return instance;
	}

	private void registerAllInterfaces(Class<?> clazz, Object instance) {
		for (Class<?> iface : clazz.getInterfaces()) {
			singletons.putIfAbsent(iface, instance);
			registerAllInterfaces(iface, instance);
		}
	}

	private void trackCloseable(Object instance) {
		if (instance instanceof AutoCloseable closeable) {
			closeables.add(closeable);
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
			registerAllInterfaces(result.getClass(), result);
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
			if (iface instanceof java.lang.reflect.ParameterizedType pt && pt.getRawType() == Provider.class) {
				return (Class<?>) pt.getActualTypeArguments()[0];
			}
		}
		throw new BeanCreationException("Could not determine provided type for: " + providerClass.getName());
	}
}
