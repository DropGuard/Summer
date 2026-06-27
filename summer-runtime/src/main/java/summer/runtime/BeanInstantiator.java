package summer.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import summer.aop.MethodInterceptor;
import summer.core.BeanContainer;
import summer.core.ErrorCode;
import summer.core.Provider;
import summer.core.bean.BeanDefinition;
import summer.core.bean.ConfigPropertiesBean;
import summer.core.config.ConfigBinder;
import summer.core.exception.BeanCreationException;
import summer.core.exception.NoSuchBeanException;

/**
 * Instantiates beans from {@link BeanDefinition}s.
 *
 * <p>
 * Handles:
 * </p>
 * <ul>
 * <li>Constructor injection</li>
 * <li>{@code @Bean} method invocation</li>
 * <li>{@link Provider} resolution</li>
 * <li>Interface registration</li>
 * <li>AOP proxy wrapping</li>
 * </ul>
 */
final class BeanInstantiator {

	private final BeanContainer.Builder builder;
	private final Map<String, List<String>> interceptorMap;

	BeanInstantiator(BeanContainer.Builder builder, Map<String, List<String>> interceptorMap) {
		this.builder = builder;
		this.interceptorMap = interceptorMap;
	}

	/**
	 * Instantiates a bean from its definition.
	 */
	void instantiateFromDefinition(BeanDefinition beanDef) {
		if (beanDef instanceof ConfigPropertiesBean cpb) {
			instantiateConfigPropertiesFromDefinition(cpb);
			return;
		}
		instantiateBean(beanDef);
	}

	private void instantiateBean(BeanDefinition bean) {
		if (builder.peek(loadClassForInstantiation(bean.qualifiedName)) != null) {
			return;
		}
		try {
			Object instance;
			if (bean.isFactoryMethod()) {
				instance = invokeFactoryMethod(bean);
			} else {
				instance = createInstance(loadClassForInstantiation(bean.qualifiedName));
			}
			registerBean(loadClassForInstantiation(bean.qualifiedName), instance);
		} catch (Exception e) {
			if (e instanceof NoSuchBeanException nse) {
				throw nse;
			}
			throw new BeanCreationException("Failed to instantiate bean: " + bean.qualifiedName, e);
		}
	}

	private Object invokeFactoryMethod(BeanDefinition fb) throws ReflectiveOperationException {
		Class<?> configClass = loadClassForInstantiation(fb.configClassName);
		Object configBean = builder.getBean(configClass);
		Class<?>[] paramTypes = fb.producerParamTypes.stream().map(cn -> loadClassForInstantiation(cn))
				.toArray(Class[]::new);
		Method producer = configClass.getMethod(fb.producerMethodName, paramTypes);
		Object[] args = resolveArgs(producer.getParameterTypes(), producer.getGenericParameterTypes());
		return producer.invoke(configBean, args);
	}

	private void instantiateConfigPropertiesFromDefinition(ConfigPropertiesBean cpb) {
		Class<?> clazz = loadClassForInstantiation(cpb.qualifiedName);
		if (builder.peek(clazz) != null) {
			return;
		}
		Object instance = ConfigBinder.bind(cpb.configPropertiesPrefix != null ? cpb.configPropertiesPrefix : "",
				clazz);
		builder.registerSingleton(clazz, instance);
	}

	private Object createInstance(Class<?> clazz) throws ReflectiveOperationException {
		Constructor<?> constructor = findSinglePublicConstructor(clazz);
		Object[] args = resolveArgs(constructor.getParameterTypes(), constructor.getGenericParameterTypes());
		return constructor.newInstance(args);
	}

	private Constructor<?> findSinglePublicConstructor(Class<?> clazz) {
		Constructor<?>[] ctors = clazz.getConstructors();
		if (ctors.length != 1) {
			throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED, "Component " + clazz.getName()
					+ " must have exactly ONE public constructor. Found: " + ctors.length);
		}
		return ctors[0];
	}

	private Object[] resolveArgs(Class<?>[] paramTypes, Type[] genericTypes) {
		Object[] args = new Object[paramTypes.length];
		for (int i = 0; i < paramTypes.length; i++) {
			Class<?> paramType = paramTypes[i];
			Type genericType = genericTypes[i];
			if (paramType == List.class && genericType instanceof ParameterizedType pt) {
				Type elementType = pt.getActualTypeArguments()[0];
				if (elementType instanceof Class<?> elementClass) {
					args[i] = builder.getBeans(elementClass);
				} else {
					args[i] = builder.getBean(paramType);
				}
			} else {
				if (paramType == summer.core.BeanContainer.class) {
					throw new BeanCreationException(ErrorCode.BEAN_CREATION_FAILED,
							"ApplicationContext injection is not supported by the runtime engine. Use BeanContainer from caller.");
				}
				args[i] = builder.getBean(paramType);
			}
		}
		return args;
	}

	private void registerBean(Class<?> clazz, Object instance) {
		if (instance instanceof Provider<?> provider) {
			registerProvider(clazz, provider);
		} else {
			registerRegularBean(clazz, instance);
		}
	}

	private void registerProvider(Class<?> clazz, Provider<?> provider) {
		Object providedInstance = provider.provide();
		Class<?> providedType = getProvidedType(clazz);
		builder.registerSingleton(providedType, providedInstance);
		builder.registerSingleton(clazz, provider);
	}

	private void registerRegularBean(Class<?> clazz, Object instance) {
		List<MethodInterceptor> matchingInterceptors = resolveMatchingInterceptors(clazz);
		Object proxy = RuntimeAopProcessor.applyProxy(instance, clazz, matchingInterceptors);
		// Concrete class key keeps the raw instance
		builder.registerSingleton(clazz, instance);
		// Interfaces get the proxy (first-wins)
		registerAllInterfaces(clazz, proxy);
	}

	private void registerAllInterfaces(Class<?> clazz, Object instance) {
		for (Class<?> iface : clazz.getInterfaces()) {
			builder.registerInterface(iface, instance);
			registerAllInterfaces(iface, instance);
		}
	}

	private List<MethodInterceptor> resolveMatchingInterceptors(Class<?> beanClass) {
		List<String> interceptorNames = interceptorMap.getOrDefault(beanClass.getName(), List.of());
		if (interceptorNames.isEmpty()) {
			return List.of();
		}
		List<MethodInterceptor> result = new ArrayList<>();
		for (String interceptorName : interceptorNames) {
			Class<?> interceptorClass = loadClassForInstantiation(interceptorName);
			Object interceptor = builder.getBean(interceptorClass);
			if (interceptor instanceof MethodInterceptor mi) {
				result.add(mi);
			}
		}
		return result;
	}

	private static Class<?> getProvidedType(Class<?> providerClass) {
		for (Type iface : providerClass.getGenericInterfaces()) {
			if (iface instanceof ParameterizedType pt && pt.getRawType() == Provider.class) {
				return (Class<?>) pt.getActualTypeArguments()[0];
			}
		}
		throw new BeanCreationException("Could not determine provided type for: " + providerClass.getName());
	}

	private static Class<?> loadClassForInstantiation(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			throw new BeanCreationException("Class not found: " + className, e);
		}
	}
}
