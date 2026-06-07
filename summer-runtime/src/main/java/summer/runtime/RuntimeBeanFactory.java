package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import summer.aop.MethodInterceptor;
import summer.core.ApplicationContext;
import summer.core.Provider;
import summer.core.exception.BeanCreationException;
import summer.core.exception.NoSuchBeanException;

class RuntimeBeanFactory {

	private final Map<Class<?>, Object> singletons;
	private final List<AutoCloseable> closeables;
	private final DependencyGraph dependencyGraph;
	private final ApplicationContext context;

	RuntimeBeanFactory(Map<Class<?>, Object> singletons, List<AutoCloseable> closeables,
			DependencyGraph dependencyGraph, ApplicationContext context) {
		this.singletons = singletons;
		this.closeables = closeables;
		this.dependencyGraph = dependencyGraph;
		this.context = context;
	}

	Object instantiateBean(Class<?> clazz) {
		if (singletons.containsKey(clazz)) {
			return singletons.get(clazz);
		}
		try {
			Object instance = createInstance(clazz);
			return registerBean(clazz, instance);
		} catch (Exception e) {
			if (e instanceof NoSuchBeanException nse)
				throw nse;
			throw new BeanCreationException("Failed to instantiate bean: " + clazz.getName(), e);
		}
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

		List<MethodInterceptor> allInterceptors = context.getBeans(MethodInterceptor.class);
		Object proxy = RuntimeAopProcessor.applyProxy(instance, clazz, allInterceptors);

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
			Class<?> producedType = producer.getReturnType();

			List<MethodInterceptor> allInterceptors = context.getBeans(MethodInterceptor.class);
			Object proxy = RuntimeAopProcessor.applyProxy(result, producedType, allInterceptors);

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
}
