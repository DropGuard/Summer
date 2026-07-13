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
 *
 * <p>
 * Parameter type information is read from {@link BeanDefinition} fields
 * ({@code constructorParamTypes}, {@code producerParamTypes},
 * {@code listElementTypes}) rather than re-derived via reflection. This ensures
 * that once {@link RuntimeBeanAdapter} populates a {@link BeanDefinition}, it
 * becomes the single source of truth.
 * </p>
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
		Class<?> clazz = loadClassForInstantiation(bean.qualifiedName);
		if (builder.peek(clazz) != null) {
			return;
		}
		try {
			Object instance;
			if (bean.isFactoryMethod()) {
				instance = invokeFactoryMethod(bean);
			} else {
				instance = createInstance(bean);
			}
			registerBean(clazz, instance);
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
		Object[] args = resolveArgsFromBeanDef(fb.producerParamTypes, fb.listElementTypes);
		return producer.invoke(configBean, args);
	}

	private void instantiateConfigPropertiesFromDefinition(ConfigPropertiesBean cpb) {
		Class<?> clazz = loadClassForInstantiation(cpb.qualifiedName);
		if (builder.peek(clazz) != null) {
			return;
		}
		Object instance = ConfigBinder.bind(cpb.configPropertiesPrefix != null ? cpb.configPropertiesPrefix : "",
				clazz);
		builder.register(clazz, instance);
	}

	private Object createInstance(BeanDefinition beanDef) throws ReflectiveOperationException {
		Class<?> clazz = loadClassForInstantiation(beanDef.qualifiedName);
		Constructor<?> constructor = findSinglePublicConstructor(clazz);
		Object[] args = resolveArgsFromBeanDef(beanDef.constructorParamTypes, beanDef.listElementTypes);
		return constructor.newInstance(args);
	}

	private Constructor<?> findSinglePublicConstructor(Class<?> clazz) {
		Constructor<?>[] ctors = clazz.getConstructors();
		if (ctors.length != 1) {
			throw new BeanCreationException("Component " + clazz.getName()
					+ " must have exactly ONE public constructor. Found: " + ctors.length);
		}
		return ctors[0];
	}

	/**
	 * Resolves constructor / {@code @Bean} method arguments from the parameter type
	 * names stored in a {@link BeanDefinition}, rather than re-deriving them from
	 * {@link java.lang.reflect.Method#getParameterTypes()} or
	 * {@link Constructor#getParameterTypes()}.
	 *
	 * <p>
	 * {@code List<T>} parameters use the element type information from
	 * {@code listElementTypes} to collect all beans of the element type.
	 * </p>
	 */
	private Object[] resolveArgsFromBeanDef(List<String> paramTypeNames, Map<Integer, String> listElementTypes) {
		Object[] args = new Object[paramTypeNames.size()];
		for (int i = 0; i < paramTypeNames.size(); i++) {
			String paramTypeName = paramTypeNames.get(i);
			if (paramTypeName.equals("summer.core.BeanContainer")) {
				throw new BeanCreationException(
						"ApplicationContext injection is not supported by the runtime engine. Use BeanContainer from caller.");
			}

			Class<?> paramType = loadClassForInstantiation(paramTypeName);
			if (paramType == List.class && listElementTypes.containsKey(i)) {
				String elementTypeName = listElementTypes.get(i);
				Class<?> elementClass = loadClassForInstantiation(elementTypeName);
				args[i] = builder.getBeans(elementClass);
			} else {
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
		builder.register(providedType, providedInstance);
		builder.register(clazz, provider);
	}

	private void registerRegularBean(Class<?> clazz, Object instance) {
		List<MethodInterceptor> matchingInterceptors = resolveMatchingInterceptors(clazz);
		Object proxy = RuntimeAopProcessor.applyProxy(instance, clazz, matchingInterceptors);
		// Concrete class key keeps the raw instance
		builder.register(clazz, instance);
		// Interfaces get the proxy (first-wins)
		registerAllInterfaces(clazz, proxy);
	}

	private void registerAllInterfaces(Class<?> clazz, Object instance) {
		for (Class<?> iface : clazz.getInterfaces()) {
			builder.register(iface, instance);
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
