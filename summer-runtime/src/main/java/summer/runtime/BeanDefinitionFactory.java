package summer.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import summer.core.BeanRegistry;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.bean.BeanDefinition;
import summer.core.config.ConfigurationProperties;

/**
 * Builds {@link BeanDefinition} objects from discovered component classes and
 * constructs AOP interceptor maps for proxy generation.
 *
 * <p>
 * This class is stateless and thread-safe. All methods accept their
 * dependencies as parameters rather than holding mutable state.
 * </p>
 */
public final class BeanDefinitionFactory {

	private BeanDefinitionFactory() {
	}

	/**
	 * Converts discovered component classes and {@code @Bean} methods into a list
	 * of {@link BeanDefinition} objects. Also includes singleton beans already
	 * registered in the registry (e.g. {@code @ConfigurationProperties}).
	 *
	 * @param componentClasses
	 *            set of discovered component classes
	 * @param registry
	 *            bean registry (for pre-bound singletons)
	 * @param adapter
	 *            runtime bean adapter for Jandex metadata
	 * @return mutable list of bean definitions
	 */
	public static List<BeanDefinition> buildBeanDefinitions(Set<Class<?>> componentClasses, BeanRegistry registry,
			RuntimeBeanAdapter adapter) {
		return componentClasses.stream().flatMap(clazz -> toBeanDefinitions(clazz, adapter))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static Stream<BeanDefinition> toBeanDefinitions(Class<?> clazz, RuntimeBeanAdapter adapter) {
		if (clazz.isAnnotationPresent(ConfigurationProperties.class)) {
			String prefix = clazz.getAnnotation(ConfigurationProperties.class).prefix();
			return Stream.of(adapter.adaptConfigProperties(clazz, prefix));
		}
		if (clazz.isAnnotationPresent(Configuration.class)) {
			Stream<BeanDefinition> configBean = Stream.of(adapter.adaptComponent(clazz));
			Stream<BeanDefinition> factoryBeans = Stream.of(clazz.getDeclaredMethods())
					.filter(m -> m.isAnnotationPresent(Bean.class)).map(adapter::adaptFactoryMethod);
			return Stream.concat(configBean, factoryBeans);
		}
		return Stream.of(adapter.adaptComponent(clazz));
	}

	/**
	 * Builds a map from bean qualifiedName to its matching interceptor
	 * qualifiedNames. Used by {@link BeanInstantiator} to apply AOP proxies.
	 *
	 * @param allBeans
	 *            list of all bean definitions
	 * @return map from bean qualifiedName to interceptor qualifiedNames
	 */
	public static Map<String, List<String>> buildInterceptorMap(List<BeanDefinition> allBeans) {
		List<BeanDefinition> interceptors = findInterceptors(allBeans);
		if (interceptors.isEmpty()) {
			return Map.of();
		}
		return allBeans.stream().filter(bean -> bean.needsProxy).filter(bean -> !isInterceptor(bean))
				.map(bean -> Map.entry(bean.qualifiedName, matchingInterceptorNames(bean, interceptors)))
				.filter(e -> !e.getValue().isEmpty()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * Populates {@link BeanDefinition#interceptors} for beans that need AOP
	 * proxying. This tells {@link summer.core.bean.SharedDependencyResolver} about
	 * AOP interceptor dependencies so that the topological sort places interceptors
	 * before their targets.
	 *
	 * @param allBeans
	 *            list of all bean definitions
	 */
	public static void populateInterceptors(List<BeanDefinition> allBeans) {
		List<BeanDefinition> interceptors = findInterceptors(allBeans);
		if (interceptors.isEmpty()) {
			return;
		}
		for (BeanDefinition bean : allBeans) {
			if (!bean.needsProxy || isInterceptor(bean)) {
				continue;
			}
			Class<?> beanClass = loadClass(bean.qualifiedName);
			if (beanClass == null) {
				continue;
			}
			interceptors.stream().filter(ib -> ib != bean).map(ib -> loadClass(ib.qualifiedName))
					.filter(ic -> ic != null && hasMatchingBinding(ic, beanClass))
					.forEach(ic -> bean.interceptors.add(findBeanByClass(allBeans, ic)));
		}
	}

	/**
	 * Checks if an interceptor has a binding annotation that matches the target
	 * class or any of its methods.
	 *
	 * @param interceptorClass
	 *            the interceptor class
	 * @param targetClass
	 *            the target class to match against
	 * @return true if the interceptor should be applied to the target
	 */
	public static boolean hasMatchingBinding(Class<?> interceptorClass, Class<?> targetClass) {
		return BindingMatcher.findBindings(interceptorClass).stream()
				.anyMatch(binding -> targetClass.isAnnotationPresent(binding)
						|| Stream.of(targetClass.getMethods()).anyMatch(m -> m.isAnnotationPresent(binding)));
	}

	/**
	 * Loads a class by name, returning null if not found.
	 *
	 * @param className
	 *            fully qualified class name
	 * @return the loaded class, or null if not found
	 */
	public static Class<?> loadClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	// ---- internal helpers ----

	private static List<BeanDefinition> findInterceptors(List<BeanDefinition> allBeans) {
		return allBeans.stream().filter(BeanDefinitionFactory::isInterceptor).toList();
	}

	private static boolean isInterceptor(BeanDefinition bean) {
		Class<?> clazz = loadClass(bean.qualifiedName);
		return clazz != null && clazz.isAnnotationPresent(summer.aop.Interceptor.class);
	}

	private static List<String> matchingInterceptorNames(BeanDefinition bean, List<BeanDefinition> interceptors) {
		Class<?> beanClass = loadClass(bean.qualifiedName);
		if (beanClass == null) {
			return List.of();
		}
		return interceptors.stream().filter(ib -> ib != bean).map(ib -> loadClass(ib.qualifiedName))
				.filter(ic -> ic != null && hasMatchingBinding(ic, beanClass)).map(Class::getName).toList();
	}

	private static BeanDefinition findBeanByClass(List<BeanDefinition> allBeans, Class<?> clazz) {
		return allBeans.stream().filter(b -> b.qualifiedName.equals(clazz.getName())).findFirst().orElse(null);
	}
}
