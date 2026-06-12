package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import summer.aop.MethodInterceptor;
import summer.core.ApplicationContext;
import summer.core.Provider;
import summer.core.config.ConfigBinder;
import summer.core.config.ConfigurationProperties;
import summer.core.exception.BeanCreationException;
import summer.core.exception.NoSuchBeanException;

class RuntimeBeanFactory {

	private final Map<Class<?>, Object> singletons;
	private final List<AutoCloseable> closeables;
	private final DependencyGraph dependencyGraph;
	private final ApplicationContext context;
	private final Map<Class<?>, Method> producers = new java.util.LinkedHashMap<>();

	RuntimeBeanFactory(Map<Class<?>, Object> singletons, List<AutoCloseable> closeables,
			DependencyGraph dependencyGraph, ApplicationContext context) {
		this.singletons = singletons;
		this.closeables = closeables;
		this.dependencyGraph = dependencyGraph;
		this.context = context;
	}

	void registerProducer(Class<?> type, Method producer) {
		producers.put(type, producer);
	}

	Set<Class<?>> getProducerTypes() {
		return producers.keySet();
	}

	/**
	 * Invokes a @Bean producer method on demand. The producer's own parameters are
	 * resolved via the context (which may trigger further lazy production).
	 */
	Object invokeProducer(Class<?> type) {
		Method producer = producers.get(type);
		if (producer == null) {
			return null;
		}
		return invokeBeanProducer(producer);
	}

	Object instantiateBean(Class<?> clazz) {
		if (singletons.containsKey(clazz)) {
			return singletons.get(clazz);
		}
		try {
			Object instance;
			if (clazz.isAnnotationPresent(summer.core.config.ConfigurationProperties.class)) {
				instance = bindConfigurationProperties(clazz);
			} else {
				instance = createInstance(clazz);
			}
			return registerBean(clazz, instance);
		} catch (Exception e) {
			if (e instanceof NoSuchBeanException nse)
				throw nse;
			throw new BeanCreationException("Failed to instantiate bean: " + clazz.getName(), e);
		}
	}

	private Object bindConfigurationProperties(Class<?> clazz) {
		ConfigurationProperties ann = clazz.getAnnotation(ConfigurationProperties.class);
		return ConfigBinder.bind(ann.prefix(), clazz);
	}

	private Object createInstance(Class<?> clazz) throws ReflectiveOperationException {
		Constructor<?> constructor = dependencyGraph.getConstructorForClass(clazz);
		Object[] dependencies = resolveArgs(constructor.getParameterTypes(), constructor.getGenericParameterTypes());
		return constructor.newInstance(dependencies);
	}
	private Object[] resolveArgs(Class<?>[] paramTypes, java.lang.reflect.Type[] genericTypes) {
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

		List<MethodInterceptor> matchingInterceptors = resolveMatchingInterceptors(clazz);
		Object proxy = RuntimeAopProcessor.applyProxy(instance, clazz, matchingInterceptors);

		// The concrete class key keeps the raw instance
		singletons.put(clazz, instance);
		// Interfaces get the proxy
		registerAllInterfaces(clazz, proxy);
		return proxy;
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

	Object invokeBeanProducer(Method producer) {
		try {
			Class<?> configClass = producer.getDeclaringClass();
			Object configBean = context.getBean(configClass);
			if (configBean == null) {
				throw new BeanCreationException("Configuration bean not instantiated: " + configClass.getName());
			}
			Object[] args = resolveProducerArgs(producer);
			Object result = producer.invoke(configBean, args);
			if (result == null) {
				return null;
			}
			Class<?> producedType = producer.getReturnType();

			List<MethodInterceptor> matchingInterceptors = resolveMatchingInterceptors(producedType);
			Object proxy = RuntimeAopProcessor.applyProxy(result, producedType, matchingInterceptors);

			singletons.put(producedType, result);
			registerAllInterfaces(producedType, proxy);
			if (result instanceof AutoCloseable closeable) {
				closeables.add(closeable);
			}
			return proxy;
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new BeanCreationException("Failed to invoke @Bean method: " + producer.getName(), e);
		}
	}

	private Object[] resolveProducerArgs(Method producer) {
		return resolveArgs(producer.getParameterTypes(), producer.getGenericParameterTypes());
	}

	private static Class<?> getProvidedType(Class<?> providerClass) {
		for (java.lang.reflect.Type iface : providerClass.getGenericInterfaces()) {
			if (iface instanceof java.lang.reflect.ParameterizedType pt && pt.getRawType() == Provider.class) {
				return (Class<?>) pt.getActualTypeArguments()[0];
			}
		}
		throw new BeanCreationException("Could not determine provided type for: " + providerClass.getName());
	}

	/**
	 * Resolves matching interceptors by querying the dependency graph (single
	 * source of truth) instead of re-computing binding matches.
	 */
	private List<MethodInterceptor> resolveMatchingInterceptors(Class<?> beanClass) {
		Set<Class<?>> interceptorClasses = dependencyGraph.getMatchingInterceptorClasses(beanClass);
		if (interceptorClasses.isEmpty()) {
			return List.of();
		}
		List<MethodInterceptor> result = new java.util.ArrayList<>();
		for (Class<?> interceptorClass : interceptorClasses) {
			Object interceptor = context.getBean(interceptorClass);
			if (interceptor instanceof MethodInterceptor mi) {
				result.add(mi);
			}
		}
		return result;
	}
}
