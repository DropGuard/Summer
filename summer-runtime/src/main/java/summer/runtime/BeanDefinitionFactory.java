package summer.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;
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
	 * of {@link BeanDefinition} objects. Pure discovery — no condition evaluation,
	 * no side effects.
	 *
	 * @param componentClasses
	 *            set of discovered component classes
	 * @param adapter
	 *            runtime bean adapter for Jandex metadata
	 * @return mutable list of bean definitions
	 */
	public static List<BeanDefinition> buildBeanDefinitions(Set<Class<?>> componentClasses,
			RuntimeBeanAdapter adapter) {
		return componentClasses.stream().flatMap(clazz -> toBeanDefinitions(clazz, adapter))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static Stream<BeanDefinition> toBeanDefinitions(Class<?> clazz, RuntimeBeanAdapter adapter) {
		Stream<BeanDefinition> result;
		String prefix = clazz.isAnnotationPresent(ConfigurationProperties.class)
				? clazz.getAnnotation(ConfigurationProperties.class).prefix()
				: "";
		if (clazz.isAnnotationPresent(ConfigurationProperties.class)) {
			// @ConfigurationProperties beans are emitted as ConfigPropertiesBean markers
			// so SharedDependencyResolver can resolve them when other beans inject them
			// (e.g. NettyServerRunner depending on ServerConfig). The actual binding
			// happens independently in
			// RuntimeBeanContainerBuilder.bindConfigurationProperties;
			// BeanInstantiator skips ConfigPropertiesBean instances because they are
			// already registered in the builder by that pass.
			result = Stream.of(adapter.adaptConfigProperties(clazz, prefix));
		} else if (clazz.isAnnotationPresent(Configuration.class)) {
			Stream<BeanDefinition> configBean = Stream.of(adapter.adaptComponent(clazz));
			Stream<BeanDefinition> factoryBeans = Stream.of(clazz.getDeclaredMethods())
					.filter(m -> m.isAnnotationPresent(Bean.class)).map(adapter::adaptFactoryMethod);
			result = Stream.concat(configBean, factoryBeans);
		} else {
			result = Stream.of(adapter.adaptComponent(clazz));
		}
		// Tag all BeanDefinitions from this class with its @Replaces target.
		// SharedConditionEvaluator reads replacesTargetClass instead of
		// querying the Jandex index — this ensures @Replaces works for
		// any component source (indexed classes AND seeds from test sources).
		Replaces replaces = clazz.getAnnotation(Replaces.class);
		if (replaces != null) {
			String targetName = replaces.value().getName();
			result = result.map(bd -> {
				bd.replacesTargetClass = targetName;
				return bd;
			});
		}
		return result;
	}

	/**
	 * Builds a map from bean qualifiedName to its matching interceptor
	 * qualifiedNames. Uses the pre-computed {@link BeanDefinition#interceptors}
	 * list populated by {@link #populateInterceptors(List)}.
	 *
	 * @param allBeans
	 *            list of all bean definitions
	 * @return map from bean qualifiedName to interceptor qualifiedNames
	 */
	public static Map<String, List<String>> buildInterceptorMap(List<BeanDefinition> allBeans) {
		return allBeans.stream().filter(BeanDefinition::needsProxy).filter(bean -> !isInterceptor(bean))
				.map(bean -> Map.entry(bean.qualifiedName, matchingInterceptorNames(bean)))
				.filter(e -> !e.getValue().isEmpty()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * Populates {@link BeanDefinition#interceptors} for beans that need AOP
	 * proxying. This tells {@link summer.core.bean.SharedDependencyResolver} about
	 * AOP interceptor dependencies so that the topological sort places interceptors
	 * before their targets.
	 *
	 * <p>
	 * Interceptor matching reads
	 * {@link BeanDefinition#interceptorBindingAnnotations} — pre-computed at
	 * discovery time by {@link RuntimeBeanAdapter} — rather than scanning
	 * annotations via reflection.
	 * </p>
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
			// needsProxy already excludes @Interceptor beans -- keep isInterceptor for
			// clarity
			if (!bean.needsProxy() || bean.isInterceptor) {
				continue;
			}
			// Pure string Set intersection on pre-computed interceptorBindingAnnotations --
			// no reflection
			interceptors.stream().filter(ib -> ib != bean).filter(ib -> hasMatchingBinding(ib, bean))
					.forEach(ib -> bean.interceptors.add(ib));
		}
	}

	/**
	 * Checks if an interceptor definition has a binding annotation that matches the
	 * target class or any of its methods.
	 *
	 * <p>
	 * Reads binding annotations from
	 * {@link BeanDefinition#interceptorBindingAnnotations}, which are pre-computed
	 * strings populated at discovery time — no reflection call to
	 * {@code findBindings()} needed.
	 * </p>
	 *
	 * @param interceptorDef
	 *            the interceptor's bean definition (with populated
	 *            {@code interceptorBindingAnnotations})
	 * @param targetClass
	 *            the target class to match against
	 * @return true if the interceptor should be applied to the target
	 */
	/**
	 * Checks if an interceptor definition has a binding annotation that matches the
	 * target definition. Pure string Set intersection on pre-computed
	 * {@link BeanDefinition#interceptorBindingAnnotations} — no reflection.
	 */
	public static boolean hasMatchingBinding(BeanDefinition interceptorDef, BeanDefinition targetDef) {
		return interceptorDef.interceptorBindingAnnotations.stream()
				.anyMatch(targetDef.interceptorBindingAnnotations::contains);
	}

	// ---- internal helpers ----

	private static List<BeanDefinition> findInterceptors(List<BeanDefinition> allBeans) {
		return allBeans.stream().filter(BeanDefinitionFactory::isInterceptor).toList();
	}

	private static boolean isInterceptor(BeanDefinition bean) {
		return bean.isInterceptor;
	}

	/**
	 * Returns the qualified names of interceptors that match the given bean.
	 *
	 * <p>
	 * Reads from the pre-computed {@link BeanDefinition#interceptors} list
	 * populated by {@link #populateInterceptors(List)} rather than re-running
	 * binding matching.
	 * </p>
	 */
	private static List<String> matchingInterceptorNames(BeanDefinition bean) {
		return bean.interceptors.stream().map(b -> b.qualifiedName).toList();
	}
}
